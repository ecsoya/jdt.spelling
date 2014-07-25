package jdt.spelling.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdt.spelling.Plugin;
import jdt.spelling.marker.MarkerFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.part.ViewPart;

public class SpellingViewPart extends ViewPart implements
		IResourceChangeListener {

	private TreeViewer viewer = null;

	private Map<String, SpellingInfo> dataMap = new HashMap<String, SpellingInfo>();

	private Map<String, List<SpellingInfo>> resourceInfos = new HashMap<String, List<SpellingInfo>>();

	private Label countLabel;

	public SpellingViewPart() {
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);

		site.getPage().addPartListener(new IPartListener() {

			@Override
			public void partOpened(IWorkbenchPart part) {
				SpellingJob job = new SpellingJob();
				job.schedule();
			}

			@Override
			public void partDeactivated(IWorkbenchPart part) {

			}

			@Override
			public void partClosed(IWorkbenchPart part) {

			}

			@Override
			public void partBroughtToTop(IWorkbenchPart part) {

			}

			@Override
			public void partActivated(IWorkbenchPart part) {

			}
		});
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite partControl = new Composite(parent, SWT.NONE);
		partControl.setLayout(new GridLayout(2, false));
		countLabel = new Label(partControl, SWT.NONE);
		Label label = new Label(partControl, SWT.NONE);
		label.setText(" incorrect spelling(s) in workspace");

		new Label(partControl, SWT.SEPARATOR | SWT.HORIZONTAL)
				.setLayoutData(new GridData(GridData.FILL,
						GridData.VERTICAL_ALIGN_END, true, false, 2, 1));
		viewer = new TreeViewer(partControl, SWT.V_SCROLL);
		GridData layoutData = new GridData(GridData.GRAB_HORIZONTAL
				| GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
		layoutData.horizontalSpan = 2;
		viewer.getControl().setLayoutData(layoutData);
		viewer.setContentProvider(new WorkbenchContentProvider() {
			@Override
			public Object[] getChildren(Object element) {
				if (element instanceof IResource) {
					String path = ((IResource) element).getFullPath()
							.toString();
					List<SpellingInfo> infos = resourceInfos.get(path);
					if (infos != null) {
						Object[] array = infos.toArray();
						Arrays.sort(array);
						return array;
					}
				}
				return super.getChildren(element);
			}

			@Override
			public boolean hasChildren(Object element) {
				if (element instanceof IResource) {
					String path = ((IResource) element).getFullPath()
							.toString();
					List<SpellingInfo> infos = resourceInfos.get(path);
					if (infos != null) {
						return !infos.isEmpty();
					}
				}
				return super.hasChildren(element);
			}
		});
		viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(
				new WorkbenchLabelProvider() {

					@Override
					public StyledString getStyledText(Object element) {
						StyledString styledText = super.getStyledText(element);
						if (element instanceof IResource) {
							String path = ((IResource) element).getFullPath()
									.toString();
							List<SpellingInfo> infos = resourceInfos.get(path);
							if (infos != null && !infos.isEmpty()) {
								styledText.append("(" + infos.size()
										+ " incorrect spelling(s))",
										new StyledString.Styler() {

											@Override
											public void applyStyles(
													TextStyle textStyle) {
												textStyle.foreground = JFaceColors
														.getErrorText(Display
																.getCurrent());
											}
										});
							}
						}
						return styledText;
					}
				}));
		viewer.setInput(ResourcesPlugin.getWorkspace());
		viewer.addFilter(new ViewerFilter() {

			@Override
			public boolean select(Viewer viewer, Object parentElement,
					Object element) {
				return isVisible(element);
			}
		});

		viewer.addDoubleClickListener(new IDoubleClickListener() {

			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) viewer
						.getSelection();
				Object firstElement = selection.getFirstElement();
				IMarker marker = null;
				if (firstElement instanceof IMarker) {
					marker = (IMarker) firstElement;
				} else if (firstElement instanceof SpellingInfo) {
					marker = ((SpellingInfo) firstElement).getMarker();
				}
				gotoMarker(marker);
			}
		});
	}

	protected boolean isVisible(Object element) {
		if (element instanceof SpellingInfo) {
			return true;
		} else if (!(element instanceof IResource)) {
			return false;
		}
		if (resourceInfos.isEmpty()) {
			return false;
		}
		IResource target = (IResource) element;
		IPath targetPath = target.getFullPath();
		Set<String> keySet = resourceInfos.keySet();
		for (String path : keySet) {
			IResource resource = ResourcesPlugin.getWorkspace().getRoot()
					.findMember(path);
			if (!resource.exists()) {
				continue;
			}
			IPath myPath = new Path(path);
			while (!myPath.equals(targetPath) && myPath.segmentCount() > 0) {
				myPath = myPath.removeLastSegments(1);
			}
			if (myPath.equals(targetPath)) {
				return true;
			}
		}
		return false;
	}

	protected void gotoMarker(IMarker marker) {
		if (marker == null || !marker.exists()) {
			return;
		}
		IWorkbenchPage page = Plugin.getActivePage();
		IEditorPart editor = page.getActiveEditor();
		if (editor != null) {
			IEditorInput input = editor.getEditorInput();
			IFile file = ResourceUtil.getFile(input);
			if (file != null) {
				if (marker.getResource().equals(file)
						&& OpenStrategy.activateOnOpen()) {
					page.activate(editor);
				}
			}
		}

		if (marker != null && marker.getResource() instanceof IFile) {
			try {
				IDE.openEditor(page, marker, OpenStrategy.activateOnOpen());
			} catch (PartInitException e) {
				Plugin.log(e);
			}
		}
	}

	@Override
	public void setFocus() {

	}

	@Override
	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		final IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}
		Job visitJob = new Job("Visit Marker changes") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					delta.accept(new IResourceDeltaVisitor() {

						@Override
						public boolean visit(IResourceDelta delta)
								throws CoreException {
							IMarkerDelta[] markerDeltas = delta
									.getMarkerDeltas();
							for (int i = 0; i < markerDeltas.length; i++) {
								IMarkerDelta markerDelta = markerDeltas[i];
								if (markerDelta
										.isSubtypeOf(MarkerFactory.JDT_SPELLING_MARKER)) {
									IMarker marker = markerDelta.getMarker();
									switch (markerDelta.getKind()) {
									case IResourceDelta.REMOVED:
										performRemoved(marker);
										break;
									case IResourceDelta.ADDED:
									case IResourceDelta.CHANGED:
										performAdded(marker);
										break;
									default:
										break;
									}

								}
							}
							return true;
						}
					});
				} catch (CoreException e) {
					return new Status(IStatus.ERROR, Plugin.getPluginId(),
							e.getMessage());
				}
				return Status.OK_STATUS;
			}
		};
		visitJob.schedule();
	}

	protected void performAdded(IMarker marker) {
		SpellingInfo info = new SpellingInfo(marker);
		String id = info.getIdentifier();
		SpellingInfo myInfo = dataMap.get(id);
		if (myInfo == null) {
			Collection<SpellingInfo> values = dataMap.values();
			for (SpellingInfo spellingInfo : values) {
				if (SpellingInfo.equals(info, spellingInfo)) {
					myInfo = spellingInfo;
					break;
				}
			}
		}
		if (myInfo == null) {
			myInfo = new SpellingInfo(marker);
			dataMap.put(myInfo.getIdentifier(), myInfo);
		} else {
			myInfo.setMarker(marker);
		}
		IResource resource = marker.getResource();
		String path = resource.getFullPath().toString();
		List<SpellingInfo> infos = resourceInfos.get(path);
		if (infos == null) {
			infos = new ArrayList<SpellingInfo>();
			resourceInfos.put(path, infos);
		}
		boolean contains = false;
		for (SpellingInfo spellingInfo : infos) {
			if (SpellingInfo.equals(myInfo, spellingInfo)) {
				contains = true;
				break;
			}
		}
		if (!contains) {
			infos.add(myInfo);
		}
		refreshUI();
	}

	protected void performRemoved(IMarker marker) {
		SpellingInfo info = new SpellingInfo(marker);
		if (!dataMap.containsKey(info.getIdentifier())) {
			IResource resource = marker.getResource();
			long id = marker.getId();
			Collection<SpellingInfo> values = dataMap.values();
			for (SpellingInfo spellingInfo : values) {
				if (resource.equals(spellingInfo.getResource())
						&& spellingInfo.contains(id)) {
					info = spellingInfo;
					break;
				}
			}
		}
		if (dataMap.containsKey(info.getIdentifier())) {
			dataMap.remove(info.getIdentifier());
		}

		String path = marker.getResource().getFullPath().toString();
		List<SpellingInfo> infos = resourceInfos.get(path);
		if (infos != null) {
			infos.remove(info);
		}
		refreshUI();
	}

	protected void refreshUI() {
		if (viewer == null || viewer.getControl() == null
				|| viewer.getControl().isDisposed()) {
			return;
		}
		viewer.getControl().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				if (countLabel != null && !countLabel.isDisposed()) {
					countLabel.setText("" + dataMap.size());
					countLabel.getParent().layout();
				}
				if (viewer == null || viewer.getControl() == null
						|| viewer.getControl().isDisposed()) {
					return;
				}
				viewer.refresh();
			}
		});
	}

}
