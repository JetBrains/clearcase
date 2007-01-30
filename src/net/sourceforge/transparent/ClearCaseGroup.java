/**
 * Copyright (c) 2004 Tripos, Inc. All rights reserved.
 *
 * User: Vincent Mallet (vmallet@gmail.com)
 * Date: Dec 8, 2004 at 3:03:22 PM
 */
package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;

public class ClearCaseGroup extends StandardVcsGroup {
    public AbstractVcs getVcs(Project project) {
        return TransparentVcs.getInstance(project);
    }
}