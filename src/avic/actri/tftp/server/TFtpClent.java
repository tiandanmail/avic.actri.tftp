/**
 * @copyright actri.avic
 */
package avic.actri.tftp.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.commons.net.tftp.TFTPClient;

/**
 * 
 * @author tdan 2022-05-18
 *
 * @implements DERIVED
 */
public class TFtpClent {

	public static void main(String[] args) throws UnknownHostException,
			FileNotFoundException, IOException {
		File downLoadFile = new File("D:\\tdan\\tmp\\a");
		download(downLoadFile, "localhost");

		File uploadFile = new File("D:\\tdan\\tmp\\b");
		upload(uploadFile, "localhost");
	}

	public static void upload(final File file, final String serverIP) {
		new Thread() {
			@Override
			public void run() {
				try {
					TFTPClient client = new TFTPClient();
					client.open();
					client.setDefaultTimeout(3000);
					client.sendFile(file.getName(), TFTPClient.BINARY_MODE,
							new FileInputStream(file), serverIP, 69);
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();

	}

	public static void download(final File dest, final String serverIP) {
		new Thread() {
			@Override
			public void run() {
				try {
					TFTPClient client = new TFTPClient();
					client.open();
					client.setDefaultTimeout(3000);
					client.receiveFile(dest.getName(), TFTPClient.BINARY_MODE,
							new FileOutputStream(dest), serverIP, 69);
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();

	}
}
