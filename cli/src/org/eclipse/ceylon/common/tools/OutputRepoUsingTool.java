package org.eclipse.ceylon.common.tools;

import java.io.File;
import java.util.ResourceBundle;

import org.eclipse.ceylon.cmr.api.ArtifactContext;
import org.eclipse.ceylon.cmr.api.RepositoryManager;
import org.eclipse.ceylon.cmr.ceylon.CeylonUtils;
import org.eclipse.ceylon.cmr.ceylon.ShaSigner;
import org.eclipse.ceylon.common.tool.Description;
import org.eclipse.ceylon.common.tool.OptionArgument;

public abstract class OutputRepoUsingTool extends RepoUsingTool {
    protected String out;
    protected String user;
    protected String pass;

    private RepositoryManager outrm;
    
    public static final String DOCSECTION_CONFIG_COMPILER =
            "## Configuration file" +
            "\n\n" +
            "The compile tool accepts the following options from the Ceylon configuration file: " +
            "`defaults.offline`, `defaults.encoding`, `compiler.source`, `compiler.resource` and `repositories` " +
            "(the equivalent options on the command line always have precedence).";
    
    public static final String DOCSECTION_REPOSITORIES =
            "## Repositories" +
            "\n\n" +
            "Repositories like those specified with the `--rep` or `--out` options can be file paths, " +
            "HTTP urls to remote servers or can be names of repositories when prepended with a `+` symbol. " +
            "These names refer to repositories defined in the configuration file or can be any of " +
            "the following predefined names `+SYSTEM`, `+CACHE`, `+LOCAL`, `+USER`, `+REMOTE` or `+MAVEN`. " +
            "For more information see https://ceylon-lang.org/documentation/1.3/reference/repository/tools";

    public OutputRepoUsingTool(ResourceBundle bundle) {
        super(bundle);
    }
    
    @OptionArgument(shortName='o', argumentName="url")
    @Description("Specifies the output module repository (which must be publishable). " +
            "(default: `./modules`)")
    public void setOut(String out) {
        this.out = out;
    }
    
    @OptionArgument(argumentName="name")
    @Description("Sets the user name for use with an authenticated output repository " +
            "(no default).")
    public void setUser(String user) {
        this.user = user;
    }
    
    @OptionArgument(argumentName="secret")
    @Description("Sets the password for use with an authenticated output repository " +
            "(no default).")
    public void setPass(String pass) {
        this.pass = pass;
    }
    
    @Override
    protected CeylonUtils.CeylonRepoManagerBuilder createRepositoryManagerBuilder() {
        return super.createRepositoryManagerBuilder()
                .outRepo(out)
                .user(user)
                .password(pass);
    }

    protected CeylonUtils.CeylonRepoManagerBuilder createOutputRepositoryManagerBuilder() {
        return super.createRepositoryManagerBuilder()
                .outRepo(out)
                .user(user)
                .password(pass);
    }
    
    protected synchronized RepositoryManager getOutputRepositoryManager() {
        if (outrm == null) {
            CeylonUtils.CeylonRepoManagerBuilder rmb = createOutputRepositoryManagerBuilder();
            outrm = rmb.buildOutputManager();
        }
        return outrm;
    }

    @Override
    protected boolean doNotReadFromOutputRepo(){
        return false;
    }

    protected void signArtifact(ArtifactContext context, File jarFile){
        ShaSigner.signArtifact(getOutputRepositoryManager(), context, jarFile, getLogger());
    }
}
