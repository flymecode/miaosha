/**
 * @author: maxu1
 * @date: 2019/1/26 15:18
 */

package com.xupt.seckill.error;

/**
 * @author maxu
 */
public interface CommonError {
	int getErrorCode();

	String getErrorMsg();

	CommonError setErrorMsg(String errorMsg);

}
