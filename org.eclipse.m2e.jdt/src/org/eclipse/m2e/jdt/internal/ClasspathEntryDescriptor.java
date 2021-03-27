/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.jdt.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.jdt.IClasspathEntryDescriptor;
import org.eclipse.m2e.jdt.IClasspathManager;


/**
 * ClasspathEntryDescriptor
 *
 * @author igor
 */
public class ClasspathEntryDescriptor implements IClasspathEntryDescriptor {

  private int entryKind;

  private IPath path;

  private boolean exported;

  private IPath outputLocation;

  private List<IAccessRule> accessRules = new ArrayList<>();

  private LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

  private IPath sourceAttachmentPath;

  private IPath sourceAttachmentRootPath;

  private LinkedHashSet<IPath> inclusionPatterns;

  private LinkedHashSet<IPath> exclusionPatterns;

  private boolean combineAccessRules;

  // maven specific attributes below

  private ArtifactKey artifactKey;

  private String scope;

  private boolean optionalDependency;

  public ClasspathEntryDescriptor(int entryKind, IPath path) {
    this.entryKind = entryKind;
    this.path = path;
  }

  public ClasspathEntryDescriptor(IClasspathEntry entry) {
    setClasspathEntry(entry);
  }

  public IClasspathEntry toClasspathEntry() {
    Map<String, String> attributes = new LinkedHashMap<>(this.attributes);

    if(artifactKey != null) {
      attributes.put(IClasspathManager.GROUP_ID_ATTRIBUTE, artifactKey.getGroupId());
      attributes.put(IClasspathManager.ARTIFACT_ID_ATTRIBUTE, artifactKey.getArtifactId());
      attributes.put(IClasspathManager.VERSION_ATTRIBUTE, artifactKey.getVersion());
      if(artifactKey.getClassifier() != null) {
        attributes.put(IClasspathManager.CLASSIFIER_ATTRIBUTE, artifactKey.getClassifier());
      }
    }
    if(scope != null) {
      attributes.put(IClasspathManager.SCOPE_ATTRIBUTE, scope);
    }
    if(optionalDependency) {
      attributes.put(IClasspathManager.OPTIONALDEPENDENCY_ATTRIBUTE, Boolean.toString(true));
    }

    IClasspathAttribute[] attributesArray = new IClasspathAttribute[attributes.size()];
    int attributeIndex = 0;
    for(Map.Entry<String, String> attribute : attributes.entrySet()) {
      attributesArray[attributeIndex++ ] = JavaCore.newClasspathAttribute(attribute.getKey(), attribute.getValue());
    }

    IAccessRule[] accessRulesArray = accessRules.toArray(new IAccessRule[accessRules.size()]);
    IClasspathEntry entry;
    switch(entryKind) {
      case IClasspathEntry.CPE_CONTAINER:
        entry = JavaCore.newContainerEntry(path, //
            accessRulesArray, //
            attributesArray, //
            exported);
        break;
      case IClasspathEntry.CPE_LIBRARY:
        entry = JavaCore.newLibraryEntry(path, //
            sourceAttachmentPath, //
            sourceAttachmentRootPath, //
            accessRulesArray, //
            attributesArray, //
            exported);
        break;
      case IClasspathEntry.CPE_SOURCE:
        entry = JavaCore.newSourceEntry(path, //
            getInclusionPatterns(), //
            getExclusionPatterns(), //
            outputLocation, //
            attributesArray);
        break;
      case IClasspathEntry.CPE_PROJECT:
        entry = JavaCore.newProjectEntry(path, //
            accessRulesArray, //
            combineAccessRules, //
            attributesArray, //
            exported);
        break;
      case IClasspathEntry.CPE_VARIABLE:
        entry = JavaCore.newVariableEntry(path, //
            sourceAttachmentPath, //
            sourceAttachmentRootPath, //
            accessRulesArray, //
            attributesArray, //
            exported);
        break;
      default:
        throw new IllegalArgumentException("Unsupported IClasspathEntry kind=" + entryKind); //$NON-NLS-1$
    }
    return entry;
  }

  public String getScope() {
    return scope;
  }

  /**
   * @return true if this entry corresponds to an optional maven dependency, false otherwise
   */
  public boolean isOptionalDependency() {
    return optionalDependency;
  }

  public void setClasspathAttribute(String name, String value) {
    if(name == null) {
      throw new NullPointerException(); // fail fast
    }
    if(value != null) {
      attributes.put(name, value);
    } else {
      attributes.remove(name);
    }
  }

  public Map<String, String> getClasspathAttributes() {
    return attributes;
  }

  public String getGroupId() {
    return artifactKey != null ? artifactKey.getGroupId() : null;
  }

  private void setClasspathEntry(IClasspathEntry entry) {
    this.entryKind = entry.getEntryKind();
    this.path = entry.getPath();
    this.exported = entry.isExported();
    this.outputLocation = entry.getOutputLocation();

    this.accessRules = new ArrayList<>();
    for(IAccessRule rule : entry.getAccessRules()) {
      this.accessRules.add(rule);
    }

    this.attributes = new LinkedHashMap<>();
    for(IClasspathAttribute attribute : entry.getExtraAttributes()) {
      attributes.put(attribute.getName(), attribute.getValue());
    }

    this.sourceAttachmentPath = entry.getSourceAttachmentPath();
    this.sourceAttachmentRootPath = entry.getSourceAttachmentRootPath();
    setInclusionPatterns(entry.getInclusionPatterns());
    setExclusionPatterns(entry.getExclusionPatterns());
    this.combineAccessRules = entry.combineAccessRules();

    String groupId = attributes.get(IClasspathManager.GROUP_ID_ATTRIBUTE);
    String artifactId = attributes.get(IClasspathManager.ARTIFACT_ID_ATTRIBUTE);
    String version = attributes.get(IClasspathManager.VERSION_ATTRIBUTE);
    String classifier = attributes.get(IClasspathManager.CLASSIFIER_ATTRIBUTE);
    if(groupId != null && artifactId != null && version != null) {
      this.artifactKey = new ArtifactKey(groupId, artifactId, version, classifier);
    }
  }

  public String getArtifactId() {
    return artifactKey != null ? artifactKey.getArtifactId() : null;
  }

  public IPath getPath() {
    return path;
  }

  public void setPath(IPath path) {
    if(path == null) {
      throw new NullPointerException();
    }
    this.path = path;
  }

  public int getEntryKind() {
    return entryKind;
  }

  public void setEntryKind(int entryKind) {
    this.entryKind = entryKind;
  }

  public ArtifactKey getArtifactKey() {
    return artifactKey;
  }

  public void setArtifactKey(ArtifactKey artifactKey) {
    this.artifactKey = artifactKey;
  }

  public void setSourceAttachment(IPath srcPath, IPath srcRoot) {
    this.sourceAttachmentPath = srcPath;
    this.sourceAttachmentRootPath = srcRoot;
  }

  public void setJavadocUrl(String javaDocUrl) {
    setClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, javaDocUrl);
  }

  public IPath getSourceAttachmentPath() {
    return sourceAttachmentPath;
  }

  public IPath getSourceAttachmentRootPath() {
    return sourceAttachmentRootPath;
  }

  public String getJavadocUrl() {
    return attributes.get(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME);
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public void setOptionalDependency(boolean optional) {
    this.optionalDependency = optional;
  }

  public void addAccessRule(IAccessRule rule) {
    this.accessRules.add(rule);
  }

  public List<IAccessRule> getAccessRules() {
    return accessRules;
  }

  public void setOutputLocation(IPath outputLocation) {
    this.outputLocation = outputLocation;
  }

  public IPath getOutputLocation() {
    return outputLocation;
  }

  public void setInclusionPatterns(IPath[] inclusionPatterns) {
    if(inclusionPatterns != null) {
      this.inclusionPatterns = new LinkedHashSet<>(Arrays.asList(inclusionPatterns));
    } else {
      this.inclusionPatterns = null;
    }
  }

  public void addInclusionPattern(IPath pattern) {
    if(inclusionPatterns == null) {
      inclusionPatterns = new LinkedHashSet<>();
    }
    inclusionPatterns.add(pattern);
  }

  public void removeInclusionPattern(IPath pattern) {
    if(inclusionPatterns != null) {
      inclusionPatterns.remove(pattern);
    }
  }

  public IPath[] getInclusionPatterns() {
    return inclusionPatterns != null ? inclusionPatterns.toArray(new IPath[inclusionPatterns.size()]) : null;
  }

  public void setExclusionPatterns(IPath[] exclusionPatterns) {
    if(exclusionPatterns != null) {
      this.exclusionPatterns = new LinkedHashSet<>(Arrays.asList(exclusionPatterns));
    } else {
      this.exclusionPatterns = null;
    }
  }

  public void addExclusionPattern(IPath pattern) {
    if(exclusionPatterns == null) {
      exclusionPatterns = new LinkedHashSet<>();
    }
    exclusionPatterns.add(pattern);
  }

  public void removeExclusionPattern(IPath pattern) {
    if(exclusionPatterns != null) {
      exclusionPatterns.remove(pattern);
    }
  }

  public IPath[] getExclusionPatterns() {
    return exclusionPatterns != null ? exclusionPatterns.toArray(new IPath[exclusionPatterns.size()]) : null;
  }

  public void setExported(boolean exported) {
    this.exported = exported;
  }

  public boolean isExported() {
    return exported;
  }

  public void setCombineAccessRules(boolean combineAccessRules) {
    this.combineAccessRules = combineAccessRules;
  }

  public boolean combineAccessRules() {
    return combineAccessRules;
  }

  public boolean isPomDerived() {
    return Boolean.parseBoolean(attributes.get(IClasspathManager.POMDERIVED_ATTRIBUTE));
  }

  public void setPomDerived(boolean derived) {
    if(derived) {
      attributes.put(IClasspathManager.POMDERIVED_ATTRIBUTE, Boolean.toString(true));
    } else {
      attributes.remove(IClasspathManager.POMDERIVED_ATTRIBUTE);
    }
  }
}
