package cn.hush.dar.common.context;


/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2025-11-22 02:47
 **/

public class BaseContext {

    public static ThreadLocal<Integer> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Integer id) {
        threadLocal.set(id);
    }

    public static Integer getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
