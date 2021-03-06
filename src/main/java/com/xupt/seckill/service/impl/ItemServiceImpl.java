/**
 * @author: maxu1
 * @date: 2019/1/27 13:04
 */

package com.xupt.seckill.service.impl;

import com.xupt.seckill.error.BusinessException;
import com.xupt.seckill.error.EmBusinessError;
import com.xupt.seckill.mapper.ItemMapper;
import com.xupt.seckill.mapper.ItemStockMapper;
import com.xupt.seckill.mapper.StockLogMapper;
import com.xupt.seckill.pojo.Item;
import com.xupt.seckill.pojo.ItemStock;
import com.xupt.seckill.pojo.StockLog;
import com.xupt.seckill.service.ItemService;
import com.xupt.seckill.service.PromoService;
import com.xupt.seckill.service.model.ItemModel;
import com.xupt.seckill.service.model.PromoModel;
import com.xupt.seckill.validator.ValidationResult;
import com.xupt.seckill.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 *
 * @author maxu
 */
@Service
public class ItemServiceImpl implements ItemService {
	@Autowired
	@Resource
	private ItemMapper itemMapper;

	@Autowired
	@Resource
	private ItemStockMapper itemStockMapper;

	@Autowired
	private ValidatorImpl validator;

	@Autowired
	private PromoService promoService;

	@Autowired
	private RedisTemplate redisTemplate;

	@Autowired
	private StockLogMapper stockLogMapper;

	@Override
	@Transactional
	public ItemModel createItem(ItemModel itemModel) throws BusinessException {
		// 校验入参
		ValidationResult result = validator.validator(itemModel);
		if(result.isHasErrors()) {
			throw new BusinessException(EmBusinessError.PARAMETER_VALDITION_ERROR,result.getErrorsMsg());
		}
		// 转化itemModel --> POJO
		Item item = convertItemFromItemModel(itemModel);
		// 写入数据库
		itemMapper.insertSelective(item);
		itemModel.setId(item.getId());
		ItemStock itemStock = convertItemStockFromItemModel(itemModel);
		itemStockMapper.insertSeclective(itemStock);
		// 返回创建完成的对象
		return this.getItemById(itemModel.getId());
	}

	@Override
	public List<ItemModel> listItem() {
		List<Item> items = itemMapper.listItem();
		List<ItemModel> itemModels = items.stream().map(item -> {
			ItemStock itemStock = itemStockMapper.findByItemId(item.getId());
			ItemModel itemModel = this.convertModelFromDataObject(item, itemStock);
			return itemModel;
		}).collect(Collectors.toList());
		return itemModels;
	}

	@Override
	public ItemModel getItemById(Integer id) {
		Item item = itemMapper.findItemById(id);
		if (item == null) {
			return null;
		}
		// 操作获得库存数量
		ItemStock itemStock = itemStockMapper.findByItemId(id);
		// 将dataObject -> model
		ItemModel itemModel = convertModelFromDataObject(item, itemStock);
		// 获取活动商品信息
		PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
		if (promoModel != null && promoModel.getStatus() != 3) {
			itemModel.setPromoModel(promoModel);
		}
		return itemModel;
	}

	@Override
	@Transactional
	public Boolean decreaseStock(Integer itemId, Integer amount) {
		// 修改内存中的库存信息
		Long result = redisTemplate.opsForValue().decrement("promo_item_stock_" + itemId, amount.intValue());
		if (result > 0) {
			// 更新库存成功
			// TODO 使用rabbitmq 异步同步库存到数据库，如果成功则返回，否则将库存信息还原，为了事务处理，我们将同步库存的操作放置在orderServiceImpl
			return true;
		} else if(result==0) {
			// 打上库存售衾的表示
			redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
			return true;
		} else {
		    // 将库存回补
            increaseStock(itemId, amount);
        }
		return false;
	}

	@Override
	public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
		itemMapper.increaseSales(itemId, amount);
	}

	@Override
	public ItemModel getItemByIdInCache(Integer id) {
		ItemModel itemModel = (ItemModel)redisTemplate.opsForValue().get("item_validate_" + id);
		if (itemModel == null) {
			itemModel = this.getItemById(id);
			redisTemplate.opsForValue().set("item_validate_" + id, itemModel, 30, TimeUnit.MINUTES);
		}
		return itemModel;
	}

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
	    // TODO 异步更新库存
        return false;
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
        return false;
    }

    @Override
    public void initStockLog(Integer itemId, Integer amount) {
		StockLog stockLog = new StockLog();
		stockLog.setAmount(amount);
		stockLog.setItemId(itemId);
		stockLog.setStatus(0);
    }

    private ItemModel convertModelFromDataObject(Item item, ItemStock itemStock) {
		ItemModel itemModel = new ItemModel();
		BeanUtils.copyProperties(item, itemModel);
		itemModel.setPrice(new BigDecimal(item.getPrice()));
		itemModel.setSales(itemStock.getStock());
		return itemModel;
	}

	private Item convertItemFromItemModel(ItemModel itemModel) {
		if (itemModel == null) {
			return null;
		}
		Item item = new Item();
		BeanUtils.copyProperties(itemModel,item);
		item.setPrice(itemModel.getPrice().doubleValue());
		return item;
	}


	private ItemStock convertItemStockFromItemModel(ItemModel itemModel) {
		if (itemModel == null) {
			return null;
		}
		ItemStock itemStock = new ItemStock();
		itemStock.setItemId(itemModel.getId());
		itemStock.setStock(itemModel.getStock());
		return itemStock;
	}
}
