package cn.hush.dar.common.exception;


import cn.hush.dar.common.errorcode.IErrorCode;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * @program: DAR
 * @description: 抽象项目中三类异常体系，客户端异常、服务端异常以及远程服务调用异常
 * @author: Hush
 * @create: 2025-06-27 00:42
 **/
@Getter
public abstract class AbstractException extends RuntimeException {

    public final String errorCode;

    public final String errorMessage;

    public AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = Optional.ofNullable(StringUtils.hasLength(message) ? message : null).orElse(errorCode.message());
    }
}
