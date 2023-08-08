/**
 * @copyright actri.avic
 */
package avic.actri.tftp.view;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * 用来输入ip地址的控件。
 * 
 * @author mxgen 2010-12-25
 * 
 * @implements SDD_IDE_RUNTIME_UI_015,SDD_DESC_IDE_RUNTIME_UI_016
 */
public class IpTextBox extends Composite {

	private Text ip_4;

	private Text ip_3;

	private Text ip_2;

	private Text ip_1;

	public IpTextBox(Composite parent, int style) {
		super(parent, style);

		setLayout(new FillLayout());
		VerifyListener verifer = new VerifyListener() {
			public void verifyText(final VerifyEvent e) {
				// e.doit = checkIp(((Text) e.getSource()).getText() + e.text);
				Text ip_text = (Text) e.getSource();
				String s = ((Text) e.getSource()).getText();
				Point p = ip_text.getSelection();
				String ss = String.copyValueOf(s.toCharArray(), 0, p.x - 0)
						+ e.text
						+ String.copyValueOf(s.toCharArray(), p.y, s.length()
								- p.y);
				e.doit = checkIp(ss);
			}
		};
		KeyListener keyl = new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				Text ip_text = (Text) e.getSource();
				if (e.keyCode > 48 && e.keyCode < 57)
					ip_text.cut();
				if (e.character == '.') {
					if (e.getSource().equals(ip_1)) {
						ip_2.setFocus();
					} else if (e.getSource().equals(ip_2)) {
						ip_3.setFocus();
					} else if (e.getSource().equals(ip_3)) {
						ip_4.setFocus();
					}
				}
			}
		};
		FocusListener focus = new FocusListener() {

			public void focusLost(FocusEvent e) {
			}

			public void focusGained(FocusEvent e) {
				Text ip_text = (Text) e.getSource();
				ip_text.setSelection(0, ip_text.getText().length());
			}
		};
		ip_1 = new Text(this, SWT.CENTER | SWT.BORDER);
		ip_1.setText("127");
		ip_1.addVerifyListener(verifer);
		ip_1.addKeyListener(keyl);
		ip_1.addFocusListener(focus);

		ip_2 = new Text(this, SWT.CENTER | SWT.BORDER);
		ip_2.setText("0");
		ip_2.addVerifyListener(verifer);
		ip_2.addKeyListener(keyl);
		ip_2.addFocusListener(focus);

		ip_3 = new Text(this, SWT.CENTER | SWT.BORDER);
		ip_3.setText("0");
		ip_3.addVerifyListener(verifer);
		ip_3.addKeyListener(keyl);
		ip_3.addFocusListener(focus);

		ip_4 = new Text(this, SWT.CENTER | SWT.BORDER);
		ip_4.setText("1");
		ip_4.addVerifyListener(verifer);
		ip_4.addFocusListener(focus);

		setSize(135, 28);
	}

	/**
	 * 获取控件中输入的IP。字符串的格式是：“xxx.xxx.xxx.xxx”。
	 * 
	 * @return IP字符串。
	 */
	public String getIp() {
		StringBuffer strbuf = new StringBuffer();
		strbuf.append(ip_1.getText().isEmpty() ? "0" : Integer.parseInt(ip_1
				.getText()));
		strbuf.append('.');
		strbuf.append(ip_2.getText().isEmpty() ? "0" : Integer.parseInt(ip_2
				.getText()));
		strbuf.append('.');
		strbuf.append(ip_3.getText().isEmpty() ? "0" : Integer.parseInt(ip_3
				.getText()));
		strbuf.append('.');
		strbuf.append(ip_4.getText().isEmpty() ? "0" : Integer.parseInt(ip_4
				.getText()));
		return strbuf.toString();
	}

	/**
	 * 设置控件中的IP。
	 * 
	 * @param ipstr
	 *            IP字符串。字符串的格式是：“xxx.xxx.xxx.xxx”。
	 */
	public void setIp(String ipstr) {
		if (!check(ipstr))
			return;
		String ips[] = ipstr.split("\\.");
		ip_1.selectAll();
		ip_1.setText(ips[0]);
		ip_2.selectAll();
		ip_2.setText(ips[1]);
		ip_3.selectAll();
		ip_3.setText(ips[2]);
		ip_4.selectAll();
		ip_4.setText(ips[3]);
		layout();
	}

	private boolean check(String ipstr) {
		if (ipstr == null)
			return false;
		String ips[] = ipstr.split("\\.");
		if (ips.length != 4)
			return false;
		for (int i = 0; i < 4; i++) {
			if (!checkIp(ips[i]))
				return false;
		}
		return true;
	}

	private boolean checkIp(String ipi) {
		if (ipi.isEmpty())
			return true;
		if (ipi.length() > 3)
			return false;
		try {
			int ip = Integer.parseInt(ipi);
			if (ip < 0 || ip > 255)
				return false;
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ip_1.setEditable(enabled);
		ip_2.setEditable(enabled);
		ip_3.setEditable(enabled);
		ip_4.setEditable(enabled);
		super.setEnabled(enabled);
	}

	public void addModifyListener(ModifyListener modifyListener) {
		ip_1.addModifyListener(modifyListener);
		ip_2.addModifyListener(modifyListener);
		ip_3.addModifyListener(modifyListener);
		ip_4.addModifyListener(modifyListener);
	}
	
}
