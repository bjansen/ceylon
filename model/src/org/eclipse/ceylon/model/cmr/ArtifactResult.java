package org.eclipse.ceylon.model.cmr;

import java.io.File;
import java.util.List;

/**
 * Artifact result.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public interface ArtifactResult {
    /**
     * Get namespace.
     *
     * @return the artifact namespace.
     */
    String namespace();

    /**
     * Get name.
     *
     * @return the artifact name.
     */
    String name();

    /**
     * Get version.
     *
     * @return the version.
     */
    String version();

    /**
     * Optional.
     *
     * @return optional
     */
    boolean optional();

    /**
     * exported.
     *
     * @return exported
     */
    boolean exported();

    /**
     * The module scope.
     *
     * @return the module scope
     */
    ModuleScope moduleScope();

    /**
     * The result type.
     *
     * @return the type
     */
    ArtifactResultType type();

    /**
     * Get visibility type.
     *
     * @return visibility type
     */
    VisibilityType visibilityType();

    /**
     * The requested artifact.
     *
     * @return the requested artifact
     * @throws RepositoryException for any I/O error
     */
    File artifact() throws RepositoryException;

    /**
     * The resources filter.
     *
     * @return the path filter or null if there is none
     */
    PathFilter filter();

    /**
     * Dependencies.
     * <p/>
     * They could be lazily recursively fetched
     * or they could be fetched in one go.
     *
     * @return dependencies, empty list if none
     * @throws RepositoryException for any I/O error
     */
    List<ArtifactResult> dependencies() throws RepositoryException;

    /**
     * Get the display string of the repository this
     * result comes from
     *
     * @return the repository display string.
     */
    String repositoryDisplayString();
    
    /**
     * Get the repository this result was resolved from.
     *
     * @return the repository this result was resolved from.
     */
    Repository repository();
    
    /**
     * List of Maven exclusions
     */
    List<Exclusion> getExclusions();
    
    String groupId();
    
    String artifactId();
    
    String classifier();
    
}
