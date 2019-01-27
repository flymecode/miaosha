/**
 * @author: maxu1
 * @date: 2019/1/27 13:02
 */

package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.ItemModel;

import java.util.List;

/**
 *
 * @author maxu
 */
public interface ItemService {
	/**
	 * 创建商品
	 * @param itemModel
	 * @return
	 */
	ItemModel createItem(ItemModel itemModel) throws BusinessException;

	/**
	 * 商品浏览
	 * @return
	 */
	List<ItemModel> listItem();

	/**
	 * 商品详情
	 * @param id
	 * @return
	 */
	ItemModel getItemById(Integer id);
}