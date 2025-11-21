package cn.hush.dar.common.exception;


import cn.hush.dar.common.errorcode.BaseErrorCode;
import cn.hush.dar.common.errorcode.IErrorCode;

/**
 * @program: DAR
 * @description: 远程服务调用异常｜比如订单调用支付失败，向上抛出的异常应该是远程服务调用异常
 * @author: Hush
 * @create: 2025-06-27 00:42
 **/

public class RemoteException extends AbstractException{

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }


}
