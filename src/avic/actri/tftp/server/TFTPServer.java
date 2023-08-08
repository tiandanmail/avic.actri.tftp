/**
 * @copyright actri.avic
 */
package avic.actri.tftp.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPAckPacket;
import org.apache.commons.net.tftp.TFTPDataPacket;
import org.apache.commons.net.tftp.TFTPErrorPacket;
import org.apache.commons.net.tftp.TFTPPacket;
import org.apache.commons.net.tftp.TFTPPacketException;
import org.apache.commons.net.tftp.TFTPReadRequestPacket;
import org.apache.commons.net.tftp.TFTPRequestPacket;
import org.apache.commons.net.tftp.TFTPWriteRequestPacket;

/**
 * 
 * @author tdan 2022-05-18
 *
 * @implements DERIVED
 */
public class TFTPServer implements Runnable {
	public static final int DEFAULT_TFTP_PORT = 69;

	public static enum ServerMode {
		GET_ONLY, PUT_ONLY, GET_AND_PUT;
	}

	private Map<String, TFTPTransfer> transfers_ = new HashMap<String, TFTPTransfer>();
	private volatile boolean shutdownServer = false;
	private TFTP serverTftp_;
	private File serverReadDirectory_;
	private File serverWriteDirectory_;
	private int port_;
	private Exception serverException = null;
	private ServerMode mode_;

	/* /dev/null output stream (default) */
	private static final PrintStream nullStream = new PrintStream(
			new OutputStream() {
				@Override
				public void write(int b) {
				}

				@Override
				public void write(byte[] b) throws IOException {
				}
			});

	// don't have access to a logger api, so we will log to these streams, which
	// by default are set to a no-op logger
	private PrintStream log_;
	private PrintStream logError_;

	private int maxTimeoutRetries_ = 3;
	private int socketTimeout_;
	private Thread serverThread;

	/**
	 * Start a TFTP Server on the default port (69). Gets and Puts occur in the
	 * specified directories.
	 * 
	 * The server will start in another thread, allowing this constructor to
	 * return immediately.
	 * 
	 * If a get or a put comes in with a relative path that tries to get outside
	 * of the serverDirectory, then the get or put will be denied.
	 * 
	 * GET_ONLY mode only allows gets, PUT_ONLY mode only allows puts, and
	 * GET_AND_PUT allows both. Modes are defined as int constants in this
	 * class.
	 * 
	 * @param serverReadDirectory
	 *            directory for GET requests
	 * @param serverWriteDirectory
	 *            directory for PUT requests
	 * @param mode
	 *            A value as specified above.
	 * @throws IOException
	 *             if the server directory is invalid or does not exist.
	 */
	public TFTPServer(File serverReadDirectory, File serverWriteDirectory,
			ServerMode mode) throws IOException {
		this(serverReadDirectory, serverWriteDirectory, DEFAULT_TFTP_PORT,
				mode, null, null);
	}

	/**
	 * Start a TFTP Server on the specified port. Gets and Puts occur in the
	 * specified directory.
	 * 
	 * The server will start in another thread, allowing this constructor to
	 * return immediately.
	 * 
	 * If a get or a put comes in with a relative path that tries to get outside
	 * of the serverDirectory, then the get or put will be denied.
	 * 
	 * GET_ONLY mode only allows gets, PUT_ONLY mode only allows puts, and
	 * GET_AND_PUT allows both. Modes are defined as int constants in this
	 * class.
	 * 
	 * @param serverReadDirectory
	 *            directory for GET requests
	 * @param serverWriteDirectory
	 *            directory for PUT requests
	 * @param mode
	 *            A value as specified above.
	 * @param log
	 *            Stream to write log message to. If not provided, uses
	 *            System.out
	 * @param errorLog
	 *            Stream to write error messages to. If not provided, uses
	 *            System.err.
	 * @throws IOException
	 *             if the server directory is invalid or does not exist.
	 */
	public TFTPServer(File serverReadDirectory, File serverWriteDirectory,
			int port, ServerMode mode, PrintStream log, PrintStream errorLog)
			throws IOException {
		port_ = port;
		mode_ = mode;
		log_ = (log == null ? nullStream : log);
		logError_ = (errorLog == null ? nullStream : errorLog);
		launch(serverReadDirectory, serverWriteDirectory);
	}

	/**
	 * Set the max number of retries in response to a timeout. Default 3. Min 0.
	 * 
	 * @param retries
	 */
	public void setMaxTimeoutRetries(int retries) {
		if (retries < 0) {
			throw new RuntimeException(Messages.TFTPServer_0);
		}
		maxTimeoutRetries_ = retries;
	}

	/**
	 * Get the current value for maxTimeoutRetries
	 */
	public int getMaxTimeoutRetries() {
		return maxTimeoutRetries_;
	}

	/**
	 * Set the socket timeout in milliseconds used in transfers. Defaults to the
	 * value here:
	 * http://commons.apache.org/net/apidocs/org/apache/commons/net/tftp
	 * /TFTP.html#DEFAULT_TIMEOUT (5000 at the time I write this) Min value of
	 * 10.
	 */
	public void setSocketTimeout(int timeout) {
		if (timeout < 10) {
			throw new RuntimeException(Messages.TFTPServer_1);
		}
		socketTimeout_ = timeout;
	}

	/**
	 * The current socket timeout used during transfers in milliseconds.
	 */
	public int getSocketTimeout() {
		return socketTimeout_;
	}

	/*
	 * start the server, throw an error if it can't start.
	 */
	private void launch(File serverReadDirectory, File serverWriteDirectory)
			throws IOException {
		log_.println(Messages.TFTPServer_2 + port_);

		serverReadDirectory_ = serverReadDirectory.getCanonicalFile();
		if (!serverReadDirectory_.exists()
				|| !serverReadDirectory.isDirectory()) {
			throw new IOException(Messages.TFTPServer_6 + serverReadDirectory_
					+ Messages.TFTPServer_7);
		}

		serverWriteDirectory_ = serverWriteDirectory.getCanonicalFile();
		if (!serverWriteDirectory_.exists()
				|| !serverWriteDirectory.isDirectory()) {
			throw new IOException(Messages.TFTPServer_8 + serverWriteDirectory_
					+ Messages.TFTPServer_9);
		}

		serverTftp_ = new TFTP();

		// This is the value used in response to each client.
		socketTimeout_ = serverTftp_.getDefaultTimeout();

		// we want the server thread to listen forever.
		serverTftp_.setDefaultTimeout(0);

		serverTftp_.open(port_);

		serverThread = new Thread(this);
		serverThread.setDaemon(true);
		serverThread.start();
	}

	@Override
	protected void finalize() throws Throwable {
		shutdown();
	}

	/**
	 * check if the server thread is still running.
	 * 
	 * @return true if running, false if stopped.
	 * @throws Exception
	 *             throws the exception that stopped the server if the server is
	 *             stopped from an exception.
	 */
	public boolean isRunning() throws Exception {
		if (shutdownServer && serverException != null) {
			throw serverException;
		}
		return !shutdownServer;
	}

	public void run() {
		try {
			while (!shutdownServer) {
				TFTPPacket tftpPacket;

				tftpPacket = serverTftp_.receive();

				TFTPTransfer tt = new TFTPTransfer(tftpPacket);
				if (tftpPacket instanceof TFTPRequestPacket) {
					synchronized (transfers_) {
						transfers_.put(
								((TFTPRequestPacket) tftpPacket).getFilename(),
								tt);
					}
				}

				Thread thread = new Thread(tt);
				thread.setDaemon(true);
				thread.start();
			}
		} catch (Exception e) {
			if (!shutdownServer) {
				serverException = e;
				logError_.println(Messages.TFTPServer_10 + e);
			}
		} finally {
			shutdownServer = true; // set this to true, so the launching thread
									// can check to see if it started.
			if (serverTftp_ != null && serverTftp_.isOpen()) {
				serverTftp_.close();
			}
		}
	}

	/**
	 * Stop the tftp server (and any currently running transfers) and release
	 * all opened network resources.
	 */
	public void shutdown() {
		shutdownServer = true;

		synchronized (transfers_) {
			Set<String> ks = transfers_.keySet();
			Iterator<String> ki = ks.iterator();
			while (ki.hasNext()) {
				transfers_.get(ki.next()).shutdown();
			}
		}

		try {
			serverTftp_.close();
		} catch (RuntimeException e) {
			// noop
		}

		try {
			serverThread.join();
		} catch (InterruptedException e) {
			// we've done the best we could, return
		}
		log_.println("TFTP Server Stopped.");
	}

	public int getReadPercent(String filename) {
		TFTPTransfer transfer = transfers_.get(filename);
		if (transfer != null) {
			return (int) (transfer.getReadPercent() * (float) 100);
		}
		return 0;
	}

	public long getReadFileLen(String filename) {
		TFTPTransfer transfer = transfers_.get(filename);
		if (transfer != null) {
			return transfer.getFilelen();
		}
		return -1;
	}

	public long getReadLen(String filename) {
		TFTPTransfer transfer = transfers_.get(filename);
		if (transfer != null) {
			return transfer.getReadlen();
		}
		return -1;
	}

	/*
	 * An instance of an ongoing transfer.
	 */
	private class TFTPTransfer implements Runnable {
		private TFTPPacket tftpPacket_;
		private long filelen = -1;

		public long getFilelen() {
			return filelen;
		}

		public long getReadlen() {
			return readlen;
		}

		private long readlen = 0;

		private boolean shutdownTransfer = false;

		TFTP transferTftp_ = null;

		public TFTPTransfer(TFTPPacket tftpPacket) {
			tftpPacket_ = tftpPacket;
		}

		public void shutdown() {
			shutdownTransfer = true;
			try {
				transferTftp_.close();
			} catch (RuntimeException e) {
				// noop
			}
		}

		public float getReadPercent() {
			return (float) readlen / (float) filelen;
		}

		public void run() {
			try {
				transferTftp_ = new TFTP();

				transferTftp_.beginBufferedOps();
				transferTftp_.setDefaultTimeout(socketTimeout_);

				transferTftp_.open();

				if (tftpPacket_ instanceof TFTPReadRequestPacket) {
					handleRead(((TFTPReadRequestPacket) tftpPacket_));
				} else if (tftpPacket_ instanceof TFTPWriteRequestPacket) {
					handleWrite((TFTPWriteRequestPacket) tftpPacket_);
				} else {
					log_.println(Messages.TFTPServer_11 + tftpPacket_
							+ Messages.TFTPServer_12);
				}
			} catch (Exception e) {
				if (!shutdownTransfer) {
					logError_.println(Messages.TFTPServer_13 + e);
				}
			} finally {
				try {
					if (transferTftp_ != null && transferTftp_.isOpen()) {
						transferTftp_.endBufferedOps();
						transferTftp_.close();
					}
				} catch (Exception e) {
					// noop
				}
				synchronized (transfers_) {
					transfers_.remove(this);
				}
			}
		}

		/*
		 * Handle a tftp read request.
		 */
		private void handleRead(TFTPReadRequestPacket trrp) throws IOException,
				TFTPPacketException {
			InputStream is = null;
			try {
				if (mode_ == ServerMode.PUT_ONLY) {
					transferTftp_.bufferedSend(new TFTPErrorPacket(trrp
							.getAddress(), trrp.getPort(),
							TFTPErrorPacket.ILLEGAL_OPERATION,
							Messages.TFTPServer_14));
					return;
				}

				try {
					is = new BufferedInputStream(new FileInputStream(
							buildSafeFile(serverReadDirectory_,
									trrp.getFilename(), false)));
					filelen = is.available();
					log_.println("Sending File " + trrp.getFilename() + " ...");
				} catch (FileNotFoundException e) {
					transferTftp_.bufferedSend(new TFTPErrorPacket(trrp
							.getAddress(), trrp.getPort(),
							TFTPErrorPacket.FILE_NOT_FOUND, e.getMessage()));
					return;
				} catch (Exception e) {
					transferTftp_.bufferedSend(new TFTPErrorPacket(trrp
							.getAddress(), trrp.getPort(),
							TFTPErrorPacket.UNDEFINED, e.getMessage()));
					return;
				}

				if (trrp.getMode() == TFTP.NETASCII_MODE) {
					is = new ToNetASCIIInputStream(is);
				}

				byte[] temp = new byte[TFTPDataPacket.MAX_DATA_LENGTH];

				TFTPPacket answer;

				int block = 1;
				boolean sendNext = true;

				int readLength = TFTPDataPacket.MAX_DATA_LENGTH;

				TFTPDataPacket lastSentData = null;

				// We are reading a file, so when we read less than the
				// requested bytes, we know that we are at the end of the file.
				while (readLength == TFTPDataPacket.MAX_DATA_LENGTH
						&& !shutdownTransfer) {
					if (sendNext) {
						readLength = is.read(temp);
						if (readLength == -1) {
							readLength = 0;
						}

						lastSentData = new TFTPDataPacket(trrp.getAddress(),
								trrp.getPort(), block, temp, 0, readLength);
						transferTftp_.bufferedSend(lastSentData);
					}

					answer = null;

					int timeoutCount = 0;

					while (!shutdownTransfer
							&& (answer == null
									|| !answer.getAddress().equals(
											trrp.getAddress()) || answer
									.getPort() != trrp.getPort())) {
						// listen for an answer.
						if (answer != null) {
							// The answer that we got didn't come from the
							// expected source, fire back an error, and continue
							// listening.
							log_.println(Messages.TFTPServer_15);
							transferTftp_.bufferedSend(new TFTPErrorPacket(
									answer.getAddress(), answer.getPort(),
									TFTPErrorPacket.UNKNOWN_TID,
									Messages.TFTPServer_16));
						}
						try {
							answer = transferTftp_.bufferedReceive();
						} catch (SocketTimeoutException e) {
							if (timeoutCount >= maxTimeoutRetries_) {
								throw e;
							}
							// didn't get an ack for this data. need to resend
							// it.
							timeoutCount++;
							transferTftp_.bufferedSend(lastSentData);
							continue;
						}
					}

					if (answer == null || !(answer instanceof TFTPAckPacket)) {
						if (!shutdownTransfer) {
							logError_.println(Messages.TFTPServer_17 + answer
									+ Messages.TFTPServer_18);
						}
						break;
					} else {
						// once we get here, we know we have an answer packet
						// from the correct host.
						TFTPAckPacket ack = (TFTPAckPacket) answer;
						if (ack.getBlockNumber() != block) {
							/*
							 * The origional tftp spec would have called on us
							 * to resend the previous data here, however, that
							 * causes the SAS Syndrome.
							 * http://www.faqs.org/rfcs/rfc1123.html section
							 * 4.2.3.1 The modified spec says that we ignore a
							 * duplicate ack. If the packet was really lost, we
							 * will time out on receive, and resend the previous
							 * data at that point.
							 */
							sendNext = false;
						} else {
							// send the next block
							block++;
							if (block > 65535) {
								// wrap the block number
								block = 0;
							}
							readlen += readLength;
							sendNext = true;
						}
					}
				}
			} finally {
				try {
					if (is != null) {
						is.close();
					}
				} catch (IOException e) {
					// noop
				}
			}
		}

		/*
		 * handle a tftp write request.
		 */
		private void handleWrite(TFTPWriteRequestPacket twrp)
				throws IOException, TFTPPacketException {
			OutputStream bos = null;
			try {
				if (mode_ == ServerMode.GET_ONLY) {
					transferTftp_.bufferedSend(new TFTPErrorPacket(twrp
							.getAddress(), twrp.getPort(),
							TFTPErrorPacket.ILLEGAL_OPERATION,
							Messages.TFTPServer_19));
					return;
				}

				int lastBlock = 0;
				String fileName = twrp.getFilename();
				log_.println("Receiving File " + twrp.getFilename() + " ...");

				try {
					File temp = buildSafeFile(serverWriteDirectory_, fileName,
							true);
					if (temp.exists()) {
						transferTftp_.bufferedSend(new TFTPErrorPacket(twrp
								.getAddress(), twrp.getPort(),
								TFTPErrorPacket.FILE_EXISTS,
								Messages.TFTPServer_20));
						return;
					}
					bos = new BufferedOutputStream(new FileOutputStream(temp));

					if (twrp.getMode() == TFTP.NETASCII_MODE) {
						bos = new FromNetASCIIOutputStream(bos);
					}
				} catch (Exception e) {
					transferTftp_.bufferedSend(new TFTPErrorPacket(twrp
							.getAddress(), twrp.getPort(),
							TFTPErrorPacket.UNDEFINED, e.getMessage()));
					return;
				}

				TFTPAckPacket lastSentAck = new TFTPAckPacket(
						twrp.getAddress(), twrp.getPort(), 0);
				transferTftp_.bufferedSend(lastSentAck);

				while (true) {
					// get the response - ensure it is from the right place.
					TFTPPacket dataPacket = null;

					int timeoutCount = 0;

					while (!shutdownTransfer
							&& (dataPacket == null
									|| !dataPacket.getAddress().equals(
											twrp.getAddress()) || dataPacket
									.getPort() != twrp.getPort())) {
						// listen for an answer.
						if (dataPacket != null) {
							// The data that we got didn't come from the
							// expected source, fire back an error, and continue
							// listening.
							log_.println(Messages.TFTPServer_21);
							transferTftp_.bufferedSend(new TFTPErrorPacket(
									dataPacket.getAddress(), dataPacket
											.getPort(),
									TFTPErrorPacket.UNKNOWN_TID,
									Messages.TFTPServer_22));
						}

						try {
							dataPacket = transferTftp_.bufferedReceive();
						} catch (SocketTimeoutException e) {
							if (timeoutCount >= maxTimeoutRetries_) {
								throw e;
							}
							// It didn't get our ack. Resend it.
							transferTftp_.bufferedSend(lastSentAck);
							timeoutCount++;
							continue;
						}
					}

					if (dataPacket != null
							&& dataPacket instanceof TFTPWriteRequestPacket) {
						// it must have missed our initial ack. Send another.
						lastSentAck = new TFTPAckPacket(twrp.getAddress(),
								twrp.getPort(), 0);
						transferTftp_.bufferedSend(lastSentAck);
					} else if (dataPacket == null
							|| !(dataPacket instanceof TFTPDataPacket)) {
						if (!shutdownTransfer) {
							logError_.println(Messages.TFTPServer_23
									+ dataPacket + Messages.TFTPServer_24);
						}
						break;
					} else {
						int block = ((TFTPDataPacket) dataPacket)
								.getBlockNumber();
						byte[] data = ((TFTPDataPacket) dataPacket).getData();
						int dataLength = ((TFTPDataPacket) dataPacket)
								.getDataLength();
						int dataOffset = ((TFTPDataPacket) dataPacket)
								.getDataOffset();

						if (block > lastBlock
								|| (lastBlock == 65535 && block == 0)) {
							// it might resend a data block if it missed our ack
							// - don't rewrite the block.
							bos.write(data, dataOffset, dataLength);
							lastBlock = block;
						}

						lastSentAck = new TFTPAckPacket(twrp.getAddress(),
								twrp.getPort(), block);
						transferTftp_.bufferedSend(lastSentAck);
						if (dataLength < TFTPDataPacket.MAX_DATA_LENGTH) {
							// end of stream signal - The tranfer is complete.
							bos.close();

							// But my ack may be lost - so listen to see if I
							// need to resend the ack.
							for (int i = 0; i < maxTimeoutRetries_; i++) {
								try {
									dataPacket = transferTftp_
											.bufferedReceive();
								} catch (SocketTimeoutException e) {
									// this is the expected route - the client
									// shouldn't be any more packets.
									break;
								}

								if (dataPacket != null
										&& (!dataPacket.getAddress().equals(
												twrp.getAddress()) || dataPacket
												.getPort() != twrp.getPort())) {
									// make sure it was from the right client...
									transferTftp_
											.bufferedSend(new TFTPErrorPacket(
													dataPacket.getAddress(),
													dataPacket.getPort(),
													TFTPErrorPacket.UNKNOWN_TID,
													Messages.TFTPServer_25));
								} else {
									// This means they sent us the last
									// datapacket again, must have missed our
									// ack. resend it.
									transferTftp_.bufferedSend(lastSentAck);
								}
							}

							// all done.
							break;
						}
					}
				}
			} finally {
				if (bos != null) {
					bos.close();
				}
			}
		}

		/*
		 * Utility method to make sure that paths provided by tftp clients do
		 * not get outside of the serverRoot directory.
		 */
		private File buildSafeFile(File serverDirectory, String fileName,
				boolean createSubDirs) throws IOException {
			File temp = new File(serverDirectory, fileName);
			temp = temp.getCanonicalFile();

			if (!isSubdirectoryOf(serverDirectory, temp)) {
				throw new IOException(Messages.TFTPServer_26);
			}

			// ensure directory exists (if requested)
			if (createSubDirs) {
				createDirectory(temp.getParentFile());
			}

			return temp;
		}

		/*
		 * recursively create subdirectories
		 */
		private void createDirectory(File file) throws IOException {
			file.mkdirs();
		}

		/*
		 * recursively check to see if one directory is a parent of another.
		 */
		private boolean isSubdirectoryOf(File parent, File child) {
			File childsParent = child.getParentFile();
			if (childsParent == null) {
				return false;
			}
			if (childsParent.equals(parent)) {
				return true;
			} else {
				return isSubdirectoryOf(parent, childsParent);
			}
		}
	}

	/**
	 * Set the stream object to log debug / informational messages. By default,
	 * this is a no-op
	 * 
	 * @param log
	 */
	public void setLog(PrintStream log) {
		this.log_ = log;
	}

	/**
	 * Set the stream object to log error messsages. By default, this is a no-op
	 * 
	 * @param logError
	 */
	public void setLogError(PrintStream logError) {
		this.logError_ = logError;
	}

	public static void main(String[] args) throws IOException {
		File dir = new File("F:\\tmp");
		TFTPServer tftpServer = new TFTPServer(dir, dir, 69,
				TFTPServer.ServerMode.GET_AND_PUT, System.out, System.err);
		tftpServer.setSocketTimeout(6000);
		Thread thread = new Thread(tftpServer);
		thread.setDaemon(false);
		thread.start();
	}
}
