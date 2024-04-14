package no.hvl.dat110.util;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash { 
	
	
	public static BigInteger hashOf(String entity) {

		BigInteger hashint = null;

		try {

			// Task: Hash a given string using MD5 and return the result as a BigInteger.

			MessageDigest md = MessageDigest.getInstance("MD5");

			// we use MD5 with 128 bits digest

			// compute the hash of the input 'entity'
			byte[] hashbytes = md.digest(entity.getBytes());

			// convert the hash into hex format
			String hashhex = String.format("%032x", new BigInteger(1, hashbytes));

			// convert the hex into BigInteger
			hashint = new BigInteger(hashhex, 16);
			// return the BigInteger

		} catch (NoSuchAlgorithmException e){
			e.printStackTrace();
		}
		return hashint;
	}
	
	public static BigInteger addressSize() {
		
		// Task: compute the address size of MD5
		
		// compute the number of bits = bitSize()
		int numBits = bitSize();
		
		// compute the address size = 2 ^ number of bits
		BigInteger addresssize = BigInteger.valueOf(2).pow(numBits);

		// return the address size
		
		return addresssize;
	}
	
	public static int bitSize() {
		
		int digestlen = 0;
		
		// find the digest length
		try {
			// find the digest length
			digestlen = MessageDigest.getInstance("MD5").getDigestLength();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		return digestlen*8;
	}
	
	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}

}
