/**
 * @copyright actri.avic
 */
package avic.actri.tftp.view;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.commons.net.tftp.TFTPClient;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import avic.actri.tftp.Activator;
import avic.actri.tftp.server.TFTPServer;
import avic.actri.tftp.server.TFTPServer.ServerMode;

/**
 * @author tdan 2022-05-18
 *
 * @implements DERIVED
 */
public class TFtpServerView extends ViewPart {

	private final static String TFTP_DIR = "TFTP_DIR";

	private Action aActionClear;
	private Action aActionDir;
	private Action aActionCnnt;
	private Action aActionDisCnnt;
	private Action aActionUpload;
	private Action aActionDownload;

	private TextViewer aTextViewer;

	private TFTPServer aTFTPServer;

	private class TFtpPrintStream extends PrintStream {
		private StyledText _styledText;;

		public TFtpPrintStream(StyledText styledText) {
			super(new OutputStream() {
				@Override
				public void write(int b) {
				}

				@Override
				public void write(byte[] b) throws IOException {
				}
			});
			_styledText = styledText;
		}

		@Override
		public void println(final String x) {
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					_styledText.append("\n" + x);
				}
			});
		}
	}

	private class DropTargetListener implements TransferDropTargetListener {
		private String path;

		public void dragEnter(DropTargetEvent event) {
			path = null;
			if (!aActionDir.isEnabled()) {
				event.detail = DND.DROP_NONE;
				return;
			}
			FileTransfer transfer = ((FileTransfer) getTransfer());
			if (!transfer.isSupportedType(event.currentDataType)) {
				event.detail = DND.DROP_NONE;
				return;
			}

			Object obj = transfer.nativeToJava(event.currentDataType);
			if (obj == null) {
				event.detail = DND.DROP_NONE;
				return;
			}
			String[] files = (String[]) obj;
			if (files.length != 1) {
				event.detail = DND.DROP_NONE;
				return;
			}
			File file = new File(files[0]);
			if (file.isFile()) {
				file = file.getParentFile();
			}
			path = file.getAbsolutePath();
		}

		public void dragLeave(DropTargetEvent event) {
		}

		public void dragOperationChanged(DropTargetEvent event) {
		}

		public void dragOver(DropTargetEvent event) {
		}

		public void drop(DropTargetEvent event) {
			if (path != null) {
				IPreferenceStore store = Activator.getDefault()
						.getPreferenceStore();
				store.setValue(TFTP_DIR, path);
				aTextViewer.getTextWidget().setText("Set Working Dir " + path);
			}
		}

		public void dropAccept(DropTargetEvent event) {
		}

		public Transfer getTransfer() {
			return FileTransfer.getInstance();
		}

		@Override
		public boolean isEnabled(DropTargetEvent event) {
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets
	 * .Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout(SWT.HORIZONTAL));
		aTextViewer = new TextViewer(parent, SWT.BORDER | SWT.READ_ONLY
				| SWT.H_SCROLL | SWT.V_SCROLL);
		StyledText styledText = aTextViewer.getTextWidget();
		styledText.setEditable(false);

		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();

		getSite().setSelectionProvider(aTextViewer);

		DropTarget dropTarget = new DropTarget(styledText, DND.DROP_MOVE);
		dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetListener());
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(aTextViewer.getControl());
		aTextViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, aTextViewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(aActionCnnt);
		manager.add(aActionDisCnnt);
		manager.add(aActionDir);
		manager.add(new Separator());
		manager.add(aActionUpload);
		manager.add(aActionDownload);
		manager.add(aActionClear);
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(aActionCnnt);
		manager.add(aActionDisCnnt);
		manager.add(aActionDir);
		manager.add(new Separator());
		manager.add(aActionUpload);
		manager.add(aActionDownload);
	}

	private void makeActions() {
		aActionCnnt = new Action() {
			public void run() {
				IPreferenceStore store = Activator.getDefault()
						.getPreferenceStore();
				String serverDir = store.getString(TFTP_DIR);
				if ("".equals(serverDir)) {
					DirectoryDialog dd = new DirectoryDialog(getSite()
							.getShell());
					dd.setFilterPath(ResourcesPlugin.getWorkspace().getRoot()
							.getFullPath().toOSString());
					serverDir = dd.open();
					if (serverDir == null) {
						return;
					}
					store.setValue(TFTP_DIR, serverDir);
					aTextViewer.getTextWidget().append(
							"Set Working Dir " + serverDir);
				}
				PrintStream stream = new TFtpPrintStream(
						aTextViewer.getTextWidget());
				File dir = new File(serverDir);
				try {
					aTFTPServer = new TFTPServer(dir, dir, 69,
							TFTPServer.ServerMode.GET_AND_PUT, stream, stream);

					aTFTPServer.setSocketTimeout(6000);
				} catch (IOException e) {
					aTextViewer.getTextWidget().append(e.getMessage());
					return;
				}

				aActionDir.setEnabled(false);
				aActionCnnt.setEnabled(false);
				aActionDisCnnt.setEnabled(true);
			}
		};
		aActionCnnt.setText("启动");
		aActionCnnt.setToolTipText("启动TFtp服务器");
		aActionCnnt.setImageDescriptor(ResourceManager
				.getPluginImageDescriptor(Activator.getDefault(),
						"icons/start.gif"));

		aActionDisCnnt = new Action() {
			public void run() {
				aTFTPServer.shutdown();
				aActionDir.setEnabled(true);
				aActionCnnt.setEnabled(true);
				aActionDisCnnt.setEnabled(false);
			}
		};
		aActionDisCnnt.setText("停止");
		aActionDisCnnt.setEnabled(false);
		aActionDisCnnt.setToolTipText("停止TFtp服务器");
		aActionDisCnnt.setImageDescriptor(ResourceManager
				.getPluginImageDescriptor(Activator.getDefault(),
						"icons/stop.gif"));

		aActionDir = new Action() {
			public void run() {
				DirectoryDialog dd = new DirectoryDialog(getSite().getShell());
				dd.setFilterPath(ResourcesPlugin.getWorkspace().getRoot()
						.getFullPath().toOSString());
				String serverDir = dd.open();
				if (serverDir != null) {
					IPreferenceStore store = Activator.getDefault()
							.getPreferenceStore();
					store.setValue(TFTP_DIR, serverDir);
					if (aTextViewer.getTextWidget().getText().length() != 0) {
						aTextViewer.getTextWidget().append("\n");
					}
					aTextViewer.getTextWidget().append(
							"Set Working Dir " + serverDir);
				}
			}
		};
		aActionDir.setText("配置");
		aActionDir.setToolTipText("配置TFTPServer目录");
		aActionDir.setImageDescriptor(PlatformUI.getWorkbench()
				.getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER));

		aActionDownload = new Action() {
			public void run() {
				TFtpClientDialog dialog = new TFtpClientDialog(getSite()
						.getShell(), 1);
				if (dialog.open() != Dialog.OK) {
					return;
				}
				String filePaht = dialog.getFilePaht();
				String serverIp = dialog.getServerip();

				tftpClient(new File(filePaht), serverIp, ServerMode.GET_ONLY);
			}
		};
		aActionDownload.setText("下载");
		aActionDownload.setToolTipText("下载文件到本地");
		aActionDownload.setImageDescriptor(ResourceManager
				.getPluginImageDescriptor(Activator.getDefault(),
						"icons/download.gif"));

		aActionUpload = new Action() {
			public void run() {
				TFtpClientDialog dialog = new TFtpClientDialog(getSite()
						.getShell(), 0);
				if (dialog.open() != Dialog.OK) {
					return;
				}
				String filePaht = dialog.getFilePaht();
				String serverIp = dialog.getServerip();
				tftpClient(new File(filePaht), serverIp, ServerMode.PUT_ONLY);
			}
		};
		aActionUpload.setText("上传");
		aActionUpload.setToolTipText("上传文件到服务器");
		aActionUpload.setImageDescriptor(ResourceManager
				.getPluginImageDescriptor(Activator.getDefault(),
						"icons/upload.gif"));

		aActionClear = new Action() {
			public void run() {
				aTextViewer.getTextWidget().setText("");
			}
		};
		aActionClear.setText("清理");
	}

	private void hookDoubleClickAction() {
	}

	private void tftpClient(final File file, final String serverIP,
			final ServerMode type) {
		Job job = new Job("TFtpClentJob") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				TFTPClient client = new TFTPClient();
				client.setDefaultTimeout(3000);
				Closeable stream = null;
				try {
					client.open();
					if (ServerMode.PUT_ONLY.equals(type)) {
						setName("Uploading " + file.getName());
						monitor.setTaskName(getName());
						FileInputStream fis = new FileInputStream(file);
						client.sendFile(file.getName(), TFTPClient.BINARY_MODE,
								fis, serverIP, 69);
						stream = fis;
					} else if (ServerMode.GET_ONLY.equals(type)) {
						setName("Downloading " + file.getName());
						monitor.setTaskName(getName());
						FileOutputStream fos = new FileOutputStream(file);
						client.receiveFile(file.getName(),
								TFTPClient.BINARY_MODE, fos, serverIP, 69);
						stream = fos;
					}
					Display.getDefault().syncExec(new Runnable() {
						public void run() {
							aTextViewer.getTextWidget().append(" Done.");
						}
					});
				} catch (Exception e) {
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
							e.getLocalizedMessage());
				} finally {
					if (stream != null) {
						try {
							stream.close();
						} catch (IOException e) {
						}
					}
				}

				monitor.done();
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
	 */
	@Override
	public void setFocus() {
		aTextViewer.getControl().setFocus();
	}
}
