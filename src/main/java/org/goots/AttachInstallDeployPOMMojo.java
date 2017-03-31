/**
 * Copyright (C) 2017 Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.goots;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Installs and deploys the specified XML (POM).
 */
@Mojo( name = "add-pom", defaultPhase = LifecyclePhase.INSTALL )
public class AttachInstallDeployPOMMojo
    extends AbstractMojo
{

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.+)::(.+)" );
    private static final String DEFAULT_INSTALL_PLUGIN_VERSION = "2.5.2";
    private static final String DEFAULT_DEPLOY_PLUGIN_VERSION = "2.8.2";


    // Used by install and deploy plugins
    /**
     * The component used to validate the user-supplied artifact coordinates.
     */
    @Component
    private ModelValidator modelValidator;

    /**
     * The project currently being build.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject mavenProject;

    /**
     * The current Maven session.
     */
    @Component
    private MavenSession mavenSession;

    /**
     * The Maven BuildPluginManager component.
     */
    @Component
    private BuildPluginManager pluginManager;

    // Needed by deploy plugin
    /**
     */
    @Component
    private ArtifactDeployer deployer;

    /**
     * Component used to create a repository.
     */
    @Component
    ArtifactRepositoryFactory repositoryFactory;

    /**
     * Specifies an alternative repository to which the project artifacts should be deployed ( other than those
     * specified in &lt;distributionManagement&gt; ). <br/>
     * Format: id::layout::url
     * <dl>
     * <dt>id</dt>
     * <dd>The id can be used to pick up the correct credentials from the settings.xml</dd>
     * <dt>layout</dt>
     * <dd>Either <code>default</code> for the Maven2 layout or <code>legacy</code> for the Maven1 layout. Maven3 also
     * uses the <code>default</code> layout.</dd>
     * <dt>url</dt>
     * <dd>The location of the repository</dd>
     * </dl>
     */
    @Parameter( property = "altDeploymentRepository" )
    private String altDeploymentRepository;

    /**
     * The alternative repository to use when the project has a snapshot version.
     *
     * @since 2.8
     * @see #altDeploymentRepository
     */
    @Parameter( property = "altSnapshotDeploymentRepository" )
    private String altSnapshotDeploymentRepository;

    /**
     * Map that contains the repository layouts.
     */
    @Component( role = ArtifactRepositoryLayout.class )
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     */
    @Parameter( defaultValue = "${localRepository}", required = true, readonly = true )
    private ArtifactRepository localRepository;


    // Plugin requirements

    /**
     * Specify the target XML pom to deploy to the specified groupId/artifactID.
     */
    @Parameter (required = true)
    private File pomName;

    /**
     * artifactId to deploy the target pom to
     */
    @Parameter (required = true)
    private String artifactId;

    /**
     * groupId to deploy the target pom to
     */
    @Parameter (required = true)
    private String groupId;

    /**
     * Whether to skip this plugin entirely.
     */
    @Parameter ( property = "add-pom.skip", defaultValue = "false" )
    private boolean skip;

    /**
     * Whether to throw an error if the target file is missing
     */
    @Parameter (defaultValue = "true")
    private boolean errorOnMissing;


    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().debug( "Skipping plugin" );
            return;
        }
        String installPluginVersion = DEFAULT_INSTALL_PLUGIN_VERSION;
        Plugin p = mavenProject.getPlugin( "org.apache.maven.plugins:maven-install-plugin" );
        if ( p != null )
        {
            installPluginVersion = p.getVersion();
        }
        String deployPluginVersion = DEFAULT_DEPLOY_PLUGIN_VERSION;
        p = mavenProject.getPlugin( "org.apache.maven.plugins:maven-deploy-plugin" );
        if ( p != null )
        {
            deployPluginVersion = p.getVersion();
        }

        if ( !pomName.exists() )
        {
            if ( errorOnMissing )
            {
                throw new MojoExecutionException( "Unable to find pomName " + pomName + " to install/deploy." );
            }
            else
            {
                getLog().warn( "Unable to find " + pomName );
                return;
            }
        }

        getLog().debug( "Running maven-install-plugin for " + pomName + " to target location " + groupId + ':' + artifactId + ':' + mavenProject.getVersion() );

        executeMojo( plugin( groupId( "org.apache.maven.plugins" ), artifactId( "maven-install-plugin" ),
                             version( installPluginVersion ) ), goal( "install-file" ),
                     configuration( element( name( "file" ), pomName.getAbsolutePath() ),
                                    element( name( "artifactId" ), artifactId ), element( name( "groupId" ), groupId ),
                                    element( name( "version" ), mavenProject.getVersion() ),
                                    element( name( "packaging" ), "pom" ) ),
                     executionEnvironment( mavenProject, mavenSession, pluginManager ) );

        // Unfortunately the maven-deploy-plugin alters the maven project within the session. This corrupts further use of it e.g.
        // in the actual deploy. Therefore, creating a partial (to avoid the mojo-executor complaining) maven project and using that
        // avoids this issue.
        MavenProject tmpProject = new MavenProject();
        tmpProject.setPluginArtifactRepositories( mavenProject.getPluginArtifactRepositories() );
        mavenSession.setCurrentProject( tmpProject );

        try
        {
            ArtifactRepository repo = getDeploymentRepository( mavenProject, altDeploymentRepository,
                                                               altSnapshotDeploymentRepository );

            getLog().debug( "Running maven-deploy-plugin for " + pomName + " to target location " + repo.getUrl() );

            executeMojo( plugin( groupId( "org.apache.maven.plugins" ), artifactId( "maven-deploy-plugin" ),
                                 version( deployPluginVersion ) ), goal( "deploy-file" ),
                         configuration( element( name( "file" ), pomName.getAbsolutePath() ),
                                        element( name( "pomFile" ), pomName.getAbsolutePath() ),
                                        element( name( "artifactId" ), artifactId ),
                                        element( name( "groupId" ), groupId ),
                                        element( name( "version" ), mavenProject.getVersion() ),
                                        element( name( "packaging" ), "pom" ), element( name( "url" ), repo.getUrl() ),
                                        element( name( "repositoryId" ), repo.getId() ) ),
                         executionEnvironment( tmpProject, mavenSession, pluginManager ) );

        }
        finally
        {
            mavenSession.setCurrentProject( mavenProject );
        }
    }

    /*
     * Below code is taken directly from maven-deploy-plugin ; maven-deploy-plugin-2.8.2:1787904/home/rnc/Work/PME/pom-deployer-maven-plugin/target/local-repo/../local-deploy/org/goots/bom/my-new-bom/1.0-SNAPSHOT/my-new-bom-1.0-20170331.111529-1.pom
     *
     */
    private ArtifactRepository getDeploymentRepository( MavenProject project, String altDeploymentRepository,
                                                String altSnapshotDeploymentRepository )
                    throws MojoExecutionException, MojoFailureException
    {
        ArtifactRepository repo = null;

        String altDeploymentRepo;
        if ( ArtifactUtils.isSnapshot( project.getVersion() ) && altSnapshotDeploymentRepository != null )
        {
            altDeploymentRepo = altSnapshotDeploymentRepository;
        }
        else
        {
            altDeploymentRepo = altDeploymentRepository;
        }

        if ( altDeploymentRepo != null )
        {
            getLog().info( "Using alternate deployment repository " + altDeploymentRepo );

            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( altDeploymentRepo );

            if ( !matcher.matches() )
            {
                throw new MojoFailureException( altDeploymentRepo, "Invalid syntax for repository.",
                                                "Invalid syntax for alternative repository. Use \"id::layout::url\"." );
            }
            else
            {
                String id = matcher.group( 1 ).trim();
                String layout = matcher.group( 2 ).trim();
                String url = matcher.group( 3 ).trim();

                ArtifactRepositoryLayout repoLayout = getLayout( layout );

                repo = repositoryFactory.createDeploymentArtifactRepository( id, url, repoLayout, true );
            }
        }

        if ( repo == null )
        {
            repo = project.getDistributionManagementArtifactRepository();
        }

        if ( repo == null )
        {
            String msg =
                            "Deployment failed: repository element was not specified in the POM inside"
                                            + " distributionManagement element or in -DaltDeploymentRepository=id::layout::url parameter";

            throw new MojoExecutionException( msg );
        }

        return repo;
    }

    private ArtifactRepositoryLayout getLayout( String id )
                    throws MojoExecutionException
    {
        ArtifactRepositoryLayout layout = repositoryLayouts.get( id );

        if ( layout == null )
        {
            throw new MojoExecutionException( "Invalid repository layout: " + id );
        }

        return layout;
    }
}
