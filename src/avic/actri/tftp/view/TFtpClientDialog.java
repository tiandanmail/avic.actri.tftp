/**
 * @copyright actri.avic
 */
package avic.actri.tftp.view;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * TFtpClientDialog
 * 
 * @implements DERIVED
 * 
 */
public class TFtpClientDialog extends TitleAreaDialog {
	private IpTextBox ipBox;
	private Text textFielPaht;
	private int type;

	private String serverip;
	private String filePaht;

	/**
	 * Create the dialog.
	 * 
	 * @param parentShell
	 * @param _type
	 *            0=上传；1=下载
	 */
	public TFtpClientDialog(Shell parentShell, int _type) {
		super(parentShell);
		type = _type;
	}

	/**
	 * Create contents of the dialog.
	 * 
	 * @param parent
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setTitle("");
		if (type == 0) {
			setTitle("上传文件到TFTP服务器");
			setMessage("请输入TFTP服务器IP地址和待上传文件的路径。");
		} else {
			setTitle("下载文件到TFTP服务器");
			setMessage("请输入TFTP服务器IP地址和待下载文件的路径。");
		}
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(3, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label lblServer = new Label(container, SWT.NONE);
		lblServer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false,
				false, 1, 1));
		lblServer.setText("服务器");

		ipBox = new IpTextBox(container, SWT.BORDER);
		ipBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2,
				1));

		Label lblFielPaht = new Label(container, SWT.NONE);
		lblFielPaht.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false,
				false, 1, 1));
		lblFielPaht.setText("文件路径");

		textFielPaht = new Text(container, SWT.BORDER);
		textFielPaht.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));
		textFielPaht.setFocus();

		Button btnFielPaht = new Button(container, SWT.NONE);
		btnFielPaht.setText("浏览");
		btnFielPaht.setEnabled(type == 0);
		btnFielPaht.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fd = new FileDialog(getShell());
				String filepaht = fd.open();
				if (filepaht != null) {
					textFielPaht.setText(filepaht);
				}
			}
		});

		return area;
	}

	/**
	 * Create contents of the button bar.
	 * 
	 * @param parent
	 */
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
				true);
		createButton(parent, IDialogConstants.CANCEL_ID,
				IDialogConstants.CANCEL_LABEL, false);
	}

	/**
	 * Return the initial size of the dialog.
	 */
	@Override
	protected Point getInitialSize() {
		return new Point(450, 300);
	}

	@Override
	public void okPressed() {
		serverip = (ipBox.getIp());
		filePaht = (textFielPaht.getText());
		super.okPressed();
	}

	public String getServerip() {
		return serverip;
	}

	public String getFilePaht() {
		return filePaht;
	}
}
