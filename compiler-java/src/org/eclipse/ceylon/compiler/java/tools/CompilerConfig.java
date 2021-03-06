package org.eclipse.ceylon.compiler.java.tools;

import java.io.File;

import org.eclipse.ceylon.common.config.CeylonConfig;
import org.eclipse.ceylon.langtools.tools.javac.main.Option;
import org.eclipse.ceylon.langtools.tools.javac.util.Context;
import org.eclipse.ceylon.langtools.tools.javac.util.Options;

public class CompilerConfig {

    public static CeylonConfig instance(Context context) {
        CeylonConfig instance = context.get(CeylonConfig.class);
        if (instance == null) {
            Options options = Options.instance(context);
            String cwd = options.get(Option.CEYLONCWD);
            if (cwd == null) {
                cwd = ".";
            }
            instance = CeylonConfig.createFromLocalDir(new File(cwd));
            context.put(CeylonConfig.class, instance);
        }
        return instance;
    }
}
