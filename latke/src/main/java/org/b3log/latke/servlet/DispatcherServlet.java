/*
 * Copyright (c) 2009-2018, b3log.org & hacpai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.latke.servlet;

import org.b3log.latke.servlet.advice.AfterRequestProcessAdvice;
import org.b3log.latke.servlet.advice.BeforeRequestProcessAdvice;
import org.b3log.latke.servlet.converter.ConvertSupport;
import org.b3log.latke.servlet.function.ContextHandler;
import org.b3log.latke.servlet.handler.*;
import org.b3log.latke.servlet.renderer.AbstractHTTPResponseRenderer;
import org.b3log.latke.servlet.renderer.HTTP404Renderer;
import org.b3log.latke.servlet.renderer.HTTP500Renderer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispatch-controller for HTTP request dispatching.
 *
 * @author <a href="mailto:wmainlove@gmail.com">Love Yao</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.2.2, Dec 2, 2018
 */
public final class DispatcherServlet extends HttpServlet {

    /**
     * Handlers.
     */
    public static final List<Handler> HANDLERS = new ArrayList<>();

    static {
        HANDLERS.add(new RequestPrepareHandler());
        HANDLERS.add(new RequestDispatchHandler());
        HANDLERS.add(new ArgsHandler());
        HANDLERS.add(new AdviceHandler());
        // here are ext handlers if exist
        HANDLERS.add(new MethodInvokeHandler());
    }

    @Override
    public void init() {
        // static resource handling must be the first one
        HANDLERS.add(0, new StaticResourceHandler(getServletContext()));
    }

    /**
     * Add the specified ext handler into handlers chain.
     *
     * @param handler the specified ext handler
     */
    public static void addHandler(final Handler handler) {
        DispatcherServlet.HANDLERS.add(DispatcherServlet.HANDLERS.size() - 1, handler);
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
        final HTTPRequestContext httpRequestContext = new HTTPRequestContext();
        httpRequestContext.setRequest(req);
        httpRequestContext.setResponse(resp);
        final HttpControl httpControl = new HttpControl(HANDLERS.iterator(), httpRequestContext);
        try {
            httpControl.nextHandler();
        } catch (final Exception e) {
            httpRequestContext.setRenderer(new HTTP500Renderer(e));
        }

        result(httpRequestContext);
    }

    /**
     * Do HTTP response.
     *
     * @param context {@link HTTPRequestContext}
     */
    public static void result(final HTTPRequestContext context) {
        final HttpServletResponse response = context.getResponse();
        if (response.isCommitted()) { // Response sends redirect or error
            return;
        }

        AbstractHTTPResponseRenderer renderer = context.getRenderer();
        if (null == renderer) {
            renderer = new HTTP404Renderer();
        }
        renderer.render(context);
    }

    /**
     * Programmatic routers.
     */
    private static List<Router> routers = new ArrayList<>();

    /**
     * HTTP DELETE routing.
     *
     * @param uriPattern the specified request URI pattern
     * @param handler    the specified handler
     * @return router
     */
    public static Router delete(final String uriPattern, final ContextHandler handler) {
        return route().delete(uriPattern, handler);
    }

    /**
     * HTTP PUT routing.
     *
     * @param uriPattern the specified request URI pattern
     * @param handler    the specified handler
     * @return router
     */
    public static Router put(final String uriPattern, final ContextHandler handler) {
        return route().put(uriPattern, handler);
    }

    /**
     * HTTP GET routing.
     *
     * @param uriPattern the specified request URI pattern
     * @param handler    the specified handler
     * @return router
     */
    public static Router get(final String uriPattern, final ContextHandler handler) {
        return route().get(uriPattern, handler);
    }

    /**
     * HTTP POST routing.
     *
     * @param uriPattern the specified request URI pattern
     * @param handler    the specified handler
     * @return router
     */
    public static Router post(final String uriPattern, final ContextHandler handler) {
        return route().post(uriPattern, handler);
    }

    /**
     * Registers a new router.
     *
     * @return router
     */
    public static Router route() {
        final Router ret = new Router();
        routers.add(ret);

        return ret;
    }

    /**
     * Performs mapping for all routers.
     */
    public static void mapping() {
        for (final Router router : routers) {
            final ProcessorInfo processorInfo = router.toProcessorInfo();
            RequestDispatchHandler.addProcessorInfo(processorInfo);
        }
    }

    /**
     * Programmatic router.
     *
     * @author <a href="http://88250.b3log.org">Liang Ding</a>
     * @version 1.0.0.0, Dec 2, 2018
     */
    public static class Router {
        private List<String> uriPatterns = new ArrayList<>();
        private URIPatternMode uriPatternMode = URIPatternMode.ANT_PATH;
        private List<HTTPRequestMethod> httpRequestMethods = new ArrayList<>();
        private Class<? extends ConvertSupport> convertSupport = ConvertSupport.class;
        private ContextHandler handler;
        private Method method;

        public Router delete(final String uriPattern, final ContextHandler handler) {
            return delete(new String[]{uriPattern}, handler);
        }

        public Router delete(final String[] uriPatterns, final ContextHandler handler) {
            return delete().uris(uriPatterns).handler(handler);
        }

        public Router put(final String uriPattern, final ContextHandler handler) {
            return put(new String[]{uriPattern}, handler);
        }

        public Router put(final String[] uriPatterns, final ContextHandler handler) {
            return put().uris(uriPatterns).handler(handler);
        }

        public Router post(final String uriPattern, final ContextHandler handler) {
            return post(new String[]{uriPattern}, handler);
        }

        public Router post(final String[] uriPatterns, final ContextHandler handler) {
            return post().uris(uriPatterns).handler(handler);
        }

        public Router get(final String uriPattern, final ContextHandler handler) {
            return get(new String[]{uriPattern}, handler);
        }

        public Router get(final String[] uriPatterns, final ContextHandler handler) {
            return get().uris(uriPatterns).handler(handler);
        }

        public Router uris(final String[] uriPatterns) {
            for (int i = 0; i < uriPatterns.length; i++) {
                uri(uriPatterns[i]);
            }

            return this;
        }

        public Router uri(final String uriPattern) {
            if (!uriPatterns.contains(uriPattern)) {
                uriPatterns.add(uriPattern);
            }

            return this;
        }

        public Router uriMode(final URIPatternMode uriPatternMode) {
            this.uriPatternMode = uriPatternMode;

            return this;
        }

        public Router get() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.GET)) {
                httpRequestMethods.add(HTTPRequestMethod.GET);
            }

            return this;
        }

        public Router post() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.POST)) {
                httpRequestMethods.add(HTTPRequestMethod.POST);
            }

            return this;
        }

        public Router delete() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.DELETE)) {
                httpRequestMethods.add(HTTPRequestMethod.DELETE);
            }

            return this;
        }

        public Router put() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.PUT)) {
                httpRequestMethods.add(HTTPRequestMethod.PUT);
            }

            return this;
        }

        public Router head() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.HEAD)) {
                httpRequestMethods.add(HTTPRequestMethod.HEAD);
            }

            return this;
        }

        public Router options() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.OPTIONS)) {
                httpRequestMethods.add(HTTPRequestMethod.OPTIONS);
            }

            return this;
        }

        public Router trace() {
            if (!httpRequestMethods.contains(HTTPRequestMethod.TRACE)) {
                httpRequestMethods.add(HTTPRequestMethod.TRACE);
            }

            return this;
        }

        public Router handler(final ContextHandler handler) {
            this.handler = handler;

            final Class clazz = handler.getClass();
            final Method method = clazz.getDeclaredMethods()[0];
            this.method = method;

            return this;
        }

        ProcessorInfo toProcessorInfo() {
            final ProcessorInfo ret = new ProcessorInfo();
            ret.setPattern(uriPatterns.toArray(new String[0]));
            ret.setUriPatternMode(uriPatternMode);
            ret.setHttpMethod(httpRequestMethods.toArray(new HTTPRequestMethod[0]));
            ret.setConvertClass(convertSupport);
            ret.setInvokeHolder(method);
            ret.setHandler(handler);

            final List<BeforeRequestProcessAdvice> beforeRequestProcessAdvices = ProcessorInfo.getBeforeList(ret);
            ret.setBeforeRequestProcessAdvices(beforeRequestProcessAdvices);
            final List<AfterRequestProcessAdvice> afterRequestProcessAdvices = ProcessorInfo.getAfterList(ret);
            ret.setAfterRequestProcessAdvices(afterRequestProcessAdvices);

            return ret;
        }
    }
}
