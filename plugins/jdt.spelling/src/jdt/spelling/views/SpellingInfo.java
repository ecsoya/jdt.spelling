package jdt.spelling.views;

import java.util.HashSet;
import java.util.Set;

import jdt.spelling.Plugin;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.model.IWorkbenchAdapter;

public class SpellingInfo implements IWorkbenchAdapter, IAdaptable,
		Comparable<SpellingInfo> {

	private IResource resource;
	private Set<Long> markerIds;

	private int line;
	private int start;
	private String message;

	public SpellingInfo(IMarker marker) {
		setMarker(marker);
	}

	public String getMessage() {
		return message;
	}

	public int getLine() {
		return line;
	}

	public IResource getResource() {
		return resource;
	}

	public IMarker getMarker() {
		if (resource == null || !resource.exists() || markerIds == null
				|| markerIds.isEmpty()) {
			return null;
		}
		for (Long id : markerIds) {
			IMarker marker = resource.getMarker(id);
			if (marker.exists()) {
				return marker;
			}
		}
		return null;
	}

	public int getStart() {
		return start;
	}

	public void setMarker(IMarker marker) {
		if (marker == null) {
			return;
		}
		this.resource = marker.getResource();
		if (markerIds == null) {
			markerIds = new HashSet<Long>();
		}
		markerIds.add(marker.getId());
		if (marker.exists()) {
			this.message = marker.getAttribute(IMarker.MESSAGE, "");
			this.line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
			this.start = marker.getAttribute(IMarker.CHAR_START, -1);
			if (line == -1 && resource != null && resource instanceof IFile) {
				ICompilationUnit cu = JavaCore
						.createCompilationUnitFrom((IFile) resource);
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setSource(cu);
				parser.setResolveBindings(true);
				CompilationUnit unit = (CompilationUnit) parser.createAST(null);
				line = unit.getLineNumber(start);
			}
		}
	}

	public String getIdentifier() {
		StringBuffer buf = new StringBuffer();
		buf.append(resource.getFullPath().toString());
		buf.append("_");
		buf.append(line);
		buf.append("_");
		buf.append(start);
		return new String(buf);
	}

	@Override
	public Object[] getChildren(Object o) {
		return new Object[0];
	}

	@Override
	public ImageDescriptor getImageDescriptor(Object object) {
		return Plugin.imageDescriptorFromPlugin("line.gif");
	}

	@Override
	public String getLabel(Object o) {
		StringBuffer labelBuf = new StringBuffer();
		labelBuf.append(line);
		labelBuf.append(": ");
		int index = message.indexOf(" ");
		if (index != -1) {
			labelBuf.append(message.substring(0, index));
		}
		return new String(labelBuf);
	}

	@Override
	public Object getParent(Object o) {
		if (this.equals(o)) {
			return resource;
		}
		return null;
	}

	public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
		if (IWorkbenchAdapter.class == adapter) {
			return this;
		}
		return null;
	}

	@Override
	public int compareTo(SpellingInfo o) {
		if (line > o.line) {
			return 1;
		} else if (line == o.line) {
			if (start > o.start) {
				return 1;
			} else if (start < o.start) {
				return -1;
			}
			return 0;
		} else {
			return -1;
		}
	}

	public boolean contains(long id) {
		if (markerIds == null) {
			return false;
		}
		return markerIds.contains(id);
	}

	public static boolean equals(SpellingInfo o1, SpellingInfo o2) {
		if (o1 == null || o2 == null) {
			return true;
		}
		IResource r1 = o1.getResource();
		IResource r2 = o2.getResource();
		if (r1 == null || r2 == null || !r1.equals(r2)) {
			return false;
		}
		if (o1.line != o2.line) {
			return false;
		}
		if (o1.start != o2.start) {
			return false;
		}
		return true;
	}

}
