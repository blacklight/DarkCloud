package it.unimore.weblab.darkcloud.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

/**
 * Class which manages common RSA utilities for the software
 * @author blacklight
 */
public abstract class CryptUtil {
	/**
	 * @return A generated RSA keypair
	 * @throws NoSuchAlgorithmException 
	 */
	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException
	{
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(4096);
		KeyPair kp = kpg.generateKeyPair();
		return kp;
	}
    
    /**
     * @return A generated AES symmetric key
     * @throws NoSuchAlgorithmException
     */
	public static SecretKey generateSymmetricKey() throws NoSuchAlgorithmException
	{
		KeyGenerator keygen = KeyGenerator.getInstance("AES");
		keygen.init(256);
        return keygen.generateKey();
	}
	
	/**
	 * Store a public key to a keyfile
	 * @param keyfile
	 * @param k Key to be stored
	 */
	public static void storePublicKey(File keyfile, Key k)
	{
		ObjectOutputStream out = null;
		
		try {
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPublicKeySpec pub = fact.getKeySpec(k, RSAPublicKeySpec.class);
			out = new ObjectOutputStream(new FileOutputStream(keyfile));
			out.writeObject(pub.getModulus());
			out.writeObject(pub.getPublicExponent());
		} catch (Exception e) {
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}
	
	/**
	 * Store a private key to a keyfile
	 * @param keyfile
	 * @param k Key to be stored
	 */
	public static void storePrivateKey(File keyfile, Key k)
	{
		ObjectOutputStream out = null;
		
		try {
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPrivateKeySpec priv = fact.getKeySpec(k, RSAPrivateKeySpec.class);
			out = new ObjectOutputStream(new FileOutputStream(keyfile));
			out.writeObject(priv.getModulus());
			out.writeObject(priv.getPrivateExponent());
		} catch (Exception e) {
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {}
			}
		}
	}
	
	/**
	 * Get a stored private key from a file
	 */
	public static Key getPrivateKey(File keyfile)
	{
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(keyfile));
			BigInteger mod = (BigInteger) in.readObject();
			BigInteger exp = (BigInteger) in.readObject();
			in.close();
			
			KeyFactory fact = KeyFactory.getInstance("RSA");
			Key k = fact.generatePrivate(new RSAPrivateKeySpec(mod, exp));
			return k;
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Get a stored public key from a file
	 */
	public static Key getPublicKey(File keyfile)
	{
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(keyfile));
			BigInteger mod = (BigInteger) in.readObject();
			BigInteger exp = (BigInteger) in.readObject();
			in.close();
			
			KeyFactory fact = KeyFactory.getInstance("RSA");
			Key k = fact.generatePublic(new RSAPublicKeySpec(mod, exp));
			return k;
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Return a base64-encoded string for a private Key object
	 * @param k Private Key object
	 * @return The base64-encoded string representation for the key
	 */
	public static String privKeyToString(Key k)
	{
		try
		{
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPrivateKeySpec priv = fact.getKeySpec(k, RSAPrivateKeySpec.class);
			return Base64.encodeBase64String(priv.getModulus().toByteArray()) + "|" +
				Base64.encodeBase64String(priv.getPrivateExponent().toByteArray());
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Return a base64-encoded string for a public Key object
	 * @param k Public Key object
	 * @return The base64-encoded string representation for the key
	 */
	public static String pubKeyToString(Key k)
	{
		try
		{
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPublicKeySpec pub = fact.getKeySpec(k, RSAPublicKeySpec.class);
			
			return Base64.encodeBase64String(pub.getModulus().toByteArray()) + "|" +
				Base64.encodeBase64String(pub.getPublicExponent().toByteArray());
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Get a private Key object from a base64-encoded string
	 * @param str Base64-encoded string
	 * @return Private Key object
	 */
	public static Key privKeyFromString(String str)
	{
		try
		{
			int pos = str.indexOf('|');
			
			if (pos == -1)
				return null;
			
			BigInteger mod = new BigInteger(Base64.decodeBase64(str.substring(0, pos).getBytes()));
			BigInteger exp = new BigInteger(Base64.decodeBase64(str.substring(pos+1).getBytes()));
			KeyFactory fact = KeyFactory.getInstance("RSA");
			return fact.generatePrivate(new RSAPrivateKeySpec(mod, exp));
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * Get a public Key object from a base64-encoded string
	 * @param str Base64-encoded string
	 * @return Public Key object
	 */
	public static Key pubKeyFromString(String str)
	{
		try
		{
			int pos = str.indexOf('|');
			
			if (pos == -1)
				return null;
			
			BigInteger mod = new BigInteger(Base64.decodeBase64(str.substring(0, pos).getBytes()));
			BigInteger exp = new BigInteger(Base64.decodeBase64(str.substring(pos+1).getBytes()));
			KeyFactory fact = KeyFactory.getInstance("RSA");
			return fact.generatePublic(new RSAPublicKeySpec(mod, exp));
		} catch (Exception e) {
			return null;
		}
	}
    
    /**
     * Get the secret key from a base64-encoded key string
     * @param str
     * @return
     */
	public static Key secretKeyFromString(String str)
	{
        try
        {
            return new SecretKeySpec(Base64.decodeBase64(str), "AES");
        } catch (Exception e){
        	return null;
        }
	}
	
	/**
	 * Sign a String content using the private key. The sign will be verified by decrypting the content using the public key
	 * @param content
	 * @param privkey
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws SignatureException 
	 */
	public static String sign(String content, PrivateKey privkey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException
	{
		Signature sign = Signature.getInstance("MD5withRSA");
		sign.initSign(privkey);
		sign.update(content.getBytes());
		return Base64.encodeBase64String(sign.sign());
	}
	
	/**
	 * Check whether the sign provided for a certain content is valid, by decrypting it through its public key
	 * @param content
	 * @param sign
	 * @param pubkey
	 * @return true if the sign is valid, false otherwise
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws SignatureException 
	 */
	public static boolean verifySign(String content, String signature, PublicKey pubkey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException
	{
		Signature sign = Signature.getInstance("MD5withRSA");
		sign.initVerify(pubkey);
		sign.update(content.getBytes());
		return sign.verify(Base64.decodeBase64(signature));
	}
    
    /**
     * Encrypt a content with a specific symmetric key and returns its byte representation
     * @param content
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
	public static byte[] encrypt(byte[] content, Key key, String algorithm) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
        Cipher cipher = Cipher.getInstance(algorithm);
        
        if (algorithm.equals("AES")) {
            cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters());
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }
        
        return cipher.doFinal(content);
	}
    
    /**
     * Decrypt a content with a specific symmetric key and returns its byte representation
     * @param content
     * @param key
     * @param algorithm
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
	public static byte[] decrypt(byte[] content, Key key, String algorithm) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException
	{
        Cipher cipher = Cipher.getInstance(algorithm);
        
        if (algorithm.equals("AES")) {
            cipher.init(Cipher.DECRYPT_MODE, key, cipher.getParameters());
        } else {
            cipher.init(Cipher.DECRYPT_MODE, key);
        }
        
        return cipher.doFinal(content);
	}
}
