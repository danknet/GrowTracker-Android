package me.anon.lib.stream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import me.anon.lib.helper.EncryptionHelper;

public class EncryptOutputStream extends OutputStream
{
	private CipherOutputStream cos;
	private FileOutputStream fos;

	public static Cipher createCipher(String key)
	{
		SecretKey secretKey = EncryptionHelper.generateKey(key);

		try
		{
			if (secretKey != null)
			{
				Cipher cipher = Cipher.getInstance("AES");
				cipher.init(Cipher.ENCRYPT_MODE, secretKey);
				return cipher;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public EncryptOutputStream(String key, File file) throws FileNotFoundException
	{
		this(createCipher(key), file);
	}

	public EncryptOutputStream(Cipher cipher, File file) throws FileNotFoundException
	{
		try
		{
			fos = new FileOutputStream(file);
			cos = new CipherOutputStream(fos, cipher);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override public void close() throws IOException
	{
		cos.close();
		fos.close();
	}

	@Override public void flush() throws IOException
	{
		cos.flush();
		fos.flush();
	}

	@Override public void write(byte[] buffer) throws IOException
	{
		cos.write(buffer);
	}

	@Override public void write(byte[] buffer, int offset, int count) throws IOException
	{
		cos.write(buffer, offset, count);
	}

	@Override public void write(int oneByte) throws IOException
	{
		cos.write(oneByte);
	}
}
