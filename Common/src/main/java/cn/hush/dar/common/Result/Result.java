package cn.hush.dar.common.Result;


import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @program: DAR
 * @description: 定义全局返回对象｜方便接口参数返回约束，避免不同的参会定义混淆前端接收
 * @author: Hush
 * @create: 2025-11-21 20:19
 **/
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 5130392244064623509L;

    /**
     * 正确返回码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 返回码
     */
    private String code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求ID
     */
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

}
