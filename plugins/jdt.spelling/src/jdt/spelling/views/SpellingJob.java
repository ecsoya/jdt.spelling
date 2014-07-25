package jdt.spelling.views;

import jdt.spelling.Plugin;
import jdt.spelling.engine.Engine;
import jdt.spelling.marker.MarkerFactory;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JavaProject;

@SuppressWarnings("restriction")
public class SpellingJob extends WorkspaceJob {

	public SpellingJob() {
		super("Check spelling in workspace");
	}

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor)
			throws CoreException {
		IWorkspace workspace = Plugin.getWorkspace();
		IProject[] projects = workspace.getRoot().getProjects();
		for (IProject project : projects) {
			if (!JavaProject.hasJavaNature(project)) {
				continue;
			}
			IJavaProject javaProject = JavaCore.create(project);
			try {
				checkSpelling(javaProject);
			} catch (Exception e) {
				Plugin.log(e);
			}
		}
		return Status.OK_STATUS;
	}

	private void clear(final IResource resource) {
		try {
			resource.deleteMarkers(MarkerFactory.JDT_SPELLING_MARKER, true,
					IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
			Plugin.log(e);
		}
	}

	private void checkSpelling(IJavaElement element) throws CoreException {
		IResource resource = element.getResource();
		if (resource != null && resource.exists()) {
			clear(resource);
		}
		Engine engine = Plugin.getDefault().getSpellEngine();
		if (engine != null) {
			engine.checkElement(element);
		}
		if (element instanceof IParent) {
			IJavaElement[] children = ((IParent) element).getChildren();
			for (IJavaElement child : children) {
				checkSpelling(child);
			}
		}
	}

}
