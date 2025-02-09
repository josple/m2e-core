/*******************************************************************************
 * Copyright (c) 2008, 2021 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.m2e.pde.connector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.markers.MavenProblemInfo;
import org.eclipse.m2e.core.internal.markers.SourceLocation;
import org.eclipse.m2e.core.internal.markers.SourceLocationHelper;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ILifecycleMappingConfiguration;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IJavaProjectConfigurator;

/**
 * This configurator performs the following tasks:
 * <ul>
 * <li>Enable the PDE nature for this project to make PDE aware of this
 * project</li>
 * <li>Set the location of the "bundle-root" where PDE looks for the
 * manifest</li>
 * </ul>
 */
@SuppressWarnings("restriction")
public class PDEMavenBundlePluginConfigurator extends AbstractProjectConfigurator implements IJavaProjectConfigurator {

	private static final String FELIX_PARAM_MANIFESTLOCATION = "manifestLocation";
	private static final String FELIX_PARAM_SUPPORTINCREMENTALBUILD = "supportIncrementalBuild";
	private static final String FELIX_MANIFEST_GOAL = "manifest";
	private static final String BND_PARAM_MANIFESTLOCATION = "manifestPath";
	private static final String[] BND_MANIFEST_GOALS = { "bnd-process", "bnd-process-tests", "jar", "test-jar" };

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		List<MojoExecution> executions = getMojoExecutions(request, monitor);
		List<MavenProblemInfo> problems = new ArrayList<MavenProblemInfo>();
		boolean hasManifestExecution = false;
		for (MojoExecution execution : executions) {
			Plugin plugin = execution.getPlugin();
			if (isFelix(plugin)) {
				MavenProject mavenProject = request.getMavenProject();
				IMaven maven = MavenPlugin.getMaven();
				if (isFelixBundleGoal(execution)) {
					Boolean supportIncremental = maven.getMojoParameterValue(mavenProject, execution,
							FELIX_PARAM_SUPPORTINCREMENTALBUILD, Boolean.class, monitor);
					if (supportIncremental == null || !supportIncremental.booleanValue()) {
						SourceLocation location = SourceLocationHelper.findLocation(execution.getPlugin(),
								"configuration");
						MavenProblemInfo problem = new MavenProblemInfo(
								"Incremental updates are currently disabled, set supportIncrementalBuild=true to support automatic manifest updates for this project.",
								IMarker.SEVERITY_WARNING, location);
						problems.add(problem);
					}
					hasManifestExecution = true;
				}
			} else if (isBND(plugin)) {
				if (isBNDBundleGoal(execution)) {
					hasManifestExecution = true;
				}
			}
		}
		if (!hasManifestExecution) {
			for (MojoExecution execution : executions) {
				SourceLocation location = SourceLocationHelper.findLocation(execution.getPlugin(), "executions");
				MavenProblemInfo problem = new MavenProblemInfo(
						"There is currently no execution that generates a manifest, consider adding an execution for one of the following goal: "
								+ (isFelix(execution.getPlugin()) ? FELIX_MANIFEST_GOAL
										: Arrays.toString(BND_MANIFEST_GOALS))
								+ ".",
						IMarker.SEVERITY_WARNING, location);
				problems.add(problem);
				break;
			}
		}
		if (problems.size() > 0) {
			this.markerManager.addErrorMarkers(request.getPom(), IMavenConstants.MARKER_LIFECYCLEMAPPING_ID, problems);
		}
		IProject project = request.getProject();
		IMavenProjectFacade facade = request.getMavenProjectFacade();

		IPath metainfPath = getMetainfPath(facade, executions, monitor);

		PDEProjectHelper.addPDENature(project, metainfPath, monitor);
	}

	private boolean isFelixBundleGoal(MojoExecution execution) {
		return FELIX_MANIFEST_GOAL.equals(execution.getGoal());
	}

	private boolean isBNDBundleGoal(MojoExecution execution) {
		String executionGoal = execution.getGoal();
		for (String goal : BND_MANIFEST_GOALS) {
			if (goal.equals(executionGoal)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor)
			throws CoreException {
	}

	@Override
	public void configureRawClasspath(ProjectConfigurationRequest request, IClasspathDescriptor classpath,
			IProgressMonitor monitor) throws CoreException {
	}

	private IPath getMetainfPath(IMavenProjectFacade facade, List<MojoExecution> executions, IProgressMonitor monitor)
			throws CoreException {
		IMaven maven = MavenPlugin.getMaven();
		for (MojoExecution execution : executions) {
			Plugin plugin = execution.getPlugin();
			MavenProject mavenProject = facade.getMavenProject(monitor);
			File location = maven.getMojoParameterValue(mavenProject, execution,
					isBND(plugin) ? BND_PARAM_MANIFESTLOCATION : FELIX_PARAM_MANIFESTLOCATION, File.class, monitor);
			if (location != null) {
				return facade.getProjectRelativePath(location.getAbsolutePath());
			}
		}
		return null;
	}

	private boolean isBND(Plugin plugin) {
		return plugin != null && "bnd-maven-plugin".equals(plugin.getArtifactId());
	}

	private boolean isFelix(Plugin plugin) {
		return plugin != null && "org.apache.felix".equals(plugin.getGroupId())
				&& "maven-bundle-plugin".equals(plugin.getArtifactId());
	}

	@Override
	public boolean hasConfigurationChanged(IMavenProjectFacade newFacade,
			ILifecycleMappingConfiguration oldProjectConfiguration, MojoExecutionKey key, IProgressMonitor monitor) {
		return false;
	}

	@Override
	public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
			IPluginExecutionMetadata executionMetadata) {
		if ((isFelix(execution.getPlugin()) && isFelixBundleGoal(execution))
				|| (isBND(execution.getPlugin()) && isBNDBundleGoal(execution))) {
			return new MojoExecutionBuildParticipant(execution, true, true);
		}
		return super.getBuildParticipant(projectFacade, execution, executionMetadata);
	}
}
