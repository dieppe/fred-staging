package freenet.crypt;

import java.math.BigInteger;

import net.i2p.util.NativeBigInteger;
import freenet.support.HexUtil;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;

public class DiffieHellmanLightContext extends KeyAgreementSchemeContext {

	/** My exponent.*/
	public final NativeBigInteger myExponent;
	/** My exponential. This is group.g ^ myExponent mod group.p */
	public final NativeBigInteger myExponential;
	/** The signature of (g^r, grpR) */
	public DSASignature signature = null;
	/** A timestamp: when was the context created ? */
	public final long lifetime = System.currentTimeMillis();

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(": myExponent=");
		sb.append(myExponent.toHexString());
		sb.append(", myExponential=");
		sb.append(myExponential.toHexString());
		
		return sb.toString();
	}

	public DiffieHellmanLightContext(NativeBigInteger myExponent, NativeBigInteger myExponential) {
		this.myExponent = myExponent;
		this.myExponential = myExponential;
		this.lastUsedTime = System.currentTimeMillis();
		this.logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
	}
	
	public void setSignature(DSASignature sig) {
		this.signature = sig;
	}
	
	/*
	 * Calling the following is costy; avoid
	 */
	public NativeBigInteger getHMACKey(NativeBigInteger peerExponential, DHGroup group) {
		lastUsedTime = System.currentTimeMillis();
		BigInteger P = group.getP();
		NativeBigInteger sharedSecret =
			(NativeBigInteger) peerExponential.modPow(myExponent, P);

		if(logMINOR) {
			Logger.minor(this, "P: "+HexUtil.biToHex(P));
			Logger.minor(this, "My exponent: "+myExponent.toHexString());
			Logger.minor(this, "My exponential: "+myExponential.toHexString());
			Logger.minor(this, "Peer's exponential: "+peerExponential.toHexString());
			Logger.minor(this, "g^ir mod p = " + sharedSecret.toHexString());
		}
		
		return sharedSecret;
	}
}