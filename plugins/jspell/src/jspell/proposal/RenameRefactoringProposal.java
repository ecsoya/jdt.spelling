package jspell.proposal;

import java.lang.reflect.InvocationTargetException;

import jspell.JSpellPlugin;
import jspell.JSpellPluginImages;
import jspell.messages.Messages;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ICommandAccess;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

/**
 * A quick assist proposal that starts the Rename refactoring.
 */
public class RenameRefactoringProposal implements IJavaCompletionProposal, ICompletionProposalExtension6,
		ICommandAccess {

	private final String label;
	private final int relevance;
	private final JavaEditor editor;
	private final IJavaElement javaElement;
	private final ICompilationUnit compilationUnit;

	private final String newName;
	private Point originalSelection;

	public RenameRefactoringProposal(JavaEditor editor, ICompilationUnit compilationUnit, IJavaElement javaElement,
			String changedWord, String newName) {
		this.editor = editor;
		this.compilationUnit = compilationUnit;
		this.javaElement = javaElement;
		this.newName = newName;
		label = Messages.RenameRefactoringProposal_Change_to + newName;
		relevance = 8;
	}

	@Override
	public void apply(IDocument document) {

		ISourceViewer viewer = editor.getViewer();
		originalSelection = viewer.getSelectedRange();

		// cretaing
		try {
			RenameJavaElementDescriptor descriptor = createRenameDescriptor(javaElement, newName);
			RenameSupport renameSupport = RenameSupport.create(descriptor);

			// running
			Shell shell = editor.getSite().getShell();

			renameSupport.perform(shell, editor.getSite().getWorkbenchWindow());
			restoreFullSelection();
			JavaModelUtil.reconcile(compilationUnit);
		} catch (JavaModelException e) {
			JSpellPlugin.log(e);
		} catch (InterruptedException e) {
			JSpellPlugin.log(e);
		} catch (InvocationTargetException e) {
			JSpellPlugin.log(e);
		} catch (CoreException e) {
			JSpellPlugin.log(e);
		}
	}

	// private ICompilationUnit getCompilationUnit() {
	// return (ICompilationUnit) EditorUtility.getEditorInputJavaElement(editor, false);
	// }

	@Override
	public Point getSelection(IDocument document) {
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return jspell.messages.Messages.RenameRefactoringProposal_additionalInfo;
	}

	@Override
	public String getDisplayString() {
		return label;
	}

	@Override
	public StyledString getStyledDisplayString() {
		return new StyledString(label);
	}

	@Override
	public Image getImage() {
		return JSpellPluginImages.getImage(JSpellPluginImages.CORRECT);
	}

	/*
	 * @see ICompletionProposal#getContextInformation()
	 */
	@Override
	public IContextInformation getContextInformation() {
		return null;
	}

	@Override
	public int getRelevance() {
		return relevance;
	}

	@Override
	public String getCommandId() {
		// return IJavaEditorActionDefinitionIds.RENAME_ELEMENT;
		return null;
	}

	/**
	 * Creates a rename descriptor.
	 * 
	 * @param javaElement
	 *            element to rename
	 * @param newName
	 *            new name
	 * @return a rename descriptor with default settings as used in the refactoring dialogs
	 * @throws JavaModelException
	 *             if an error occurs while accessing the element
	 */
	private RenameJavaElementDescriptor createRenameDescriptor(IJavaElement javaElement, String newName)
			throws JavaModelException {

		int elementType = javaElement.getElementType();

		String contributionId;
		switch (elementType) {
		case IJavaElement.JAVA_PROJECT:
			contributionId = IJavaRefactorings.RENAME_JAVA_PROJECT;
			break;
		case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			contributionId = IJavaRefactorings.RENAME_SOURCE_FOLDER;
			break;
		case IJavaElement.PACKAGE_FRAGMENT:
			contributionId = IJavaRefactorings.RENAME_PACKAGE;
			break;
		case IJavaElement.COMPILATION_UNIT:
			contributionId = IJavaRefactorings.RENAME_COMPILATION_UNIT;
			break;
		case IJavaElement.TYPE:
			contributionId = IJavaRefactorings.RENAME_TYPE;
			break;
		case IJavaElement.METHOD:
			final IMethod method = (IMethod) javaElement;
			if (method.isConstructor()) {
				return createRenameDescriptor(method.getDeclaringType(), newName);
			} else {
				contributionId = IJavaRefactorings.RENAME_METHOD;
			}
			break;
		case IJavaElement.FIELD:
			IField field = (IField) javaElement;
			if (field.isEnumConstant()) {
				contributionId = IJavaRefactorings.RENAME_ENUM_CONSTANT;
			} else {
				contributionId = IJavaRefactorings.RENAME_FIELD;
			}
			break;
		case IJavaElement.TYPE_PARAMETER:
			contributionId = IJavaRefactorings.RENAME_TYPE_PARAMETER;
			break;
		case IJavaElement.LOCAL_VARIABLE:
			contributionId = IJavaRefactorings.RENAME_LOCAL_VARIABLE;
			break;
		default:
			return null;
		}

		RenameJavaElementDescriptor descriptor = new RenameJavaElementDescriptor(contributionId);
		descriptor.setJavaElement(javaElement);
		descriptor.setNewName(newName);

		if (elementType != IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			descriptor.setUpdateReferences(true);
		}

		switch (elementType) {
		case IJavaElement.PACKAGE_FRAGMENT:
			descriptor.setUpdateHierarchy(true);
		}

		switch (elementType) {
		case IJavaElement.PACKAGE_FRAGMENT:
		case IJavaElement.TYPE:
		case IJavaElement.FIELD:
			descriptor.setUpdateTextualOccurrences(true);
		}
		switch (elementType) {
		case IJavaElement.FIELD:
			descriptor.setRenameGetters(true);
			descriptor.setRenameSetters(true);
		}
		return descriptor;
	}

	private void restoreFullSelection() {
		if (originalSelection.y != 0) {
			int originalOffset = originalSelection.x;
			editor.getViewer().setSelectedRange(originalOffset, newName.length());
		}
	}
}
