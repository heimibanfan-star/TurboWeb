package top.turboweb.http.router;

import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.validation.BeanValidator;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

public class RouteHandlerInvoker {

    private final HttpMethod httpMethod;
    private final MethodHandle methodHandle;
    private final ParameterParser.Binder[] binders;

    /**
     * 构造单一请求方式下的路由调用器
     *
     * @param httpMethod 本调用器对应的请求方式,不能为 null
     * @param methodHandle 已绑定接收者、可按参数数组展开调用的目标方法句柄,不能为 null
     * @param binders 与目标方法形参一一对应的参数绑定器,不能为 null
     * @throws NullPointerException 任一参数为 null
     */
    public RouteHandlerInvoker(
            HttpMethod httpMethod,
            MethodHandle methodHandle,
            ParameterParser.Binder[] binders
    ) {
        Objects.requireNonNull(httpMethod, "HttpMethod must not be null");
        Objects.requireNonNull(methodHandle, "MethodHandle must not be null");
        Objects.requireNonNull(binders, "Binder[] must not be null");
        this.httpMethod = httpMethod;
        this.methodHandle = methodHandle;
        this.binders = binders;
    }

    /**
     * 逐位绑定请求上下文中的参数,展开后调用目标方法
     *
     * @param context 请求上下文
     * @param beanValidator Bean 校验器,随上下文交给各绑定器,绑定结果为 bean 需校验时由绑定器经其执行
     * @return 目标方法的返回值
     * @throws Throwable 参数绑定或目标方法调用抛出的异常原样透传
     */
    public Object invoke(HttpContext context, BeanValidator beanValidator) throws Throwable {
        Object[] args = new Object[binders.length];
        for (int i = 0; i < binders.length; i++) {
            args[i] = binders[i].bindParameter(context, beanValidator);
        }
        return methodHandle.invoke(args);
    }

    /**
     * 本调用器对应的请求方式
     *
     * @return 请求方式
     */
    public HttpMethod httpMethod() {
        return this.httpMethod;
    }
}
