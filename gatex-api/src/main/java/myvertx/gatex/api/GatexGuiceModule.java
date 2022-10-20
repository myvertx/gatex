package myvertx.gatex.api;

import com.google.inject.Module;

import java.util.List;

/**
 * guice模块
 *
 * @author zbz
 */
public interface GatexGuiceModule {

    /**
     * 添加guice模块
     *
     * @param modules 将模块添加到此列表
     */
    void add(List<Module> modules);

}
