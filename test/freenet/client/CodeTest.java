package freenet.client;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.onionnetworks.fec.FECCode;
import com.onionnetworks.fec.FECCodeFactory;
import com.onionnetworks.fec.FECMath;
import com.onionnetworks.fec.PureCode;
import com.onionnetworks.util.Buffer;
import com.onionnetworks.util.Util;

public class CodeTest extends TestCase {

	private static final boolean BENCHMARK = Boolean.getBoolean("benchmark");
	public static FECMath fecMath = new FECMath(8);

	public static final int KK = 192;
	public static final int PACKET_SIZE = 4096;

	/**
	 * Creates k packets of size sz of random data, encodes them, and tries to decode. Index
	 * contains the permutation entry.
	 */
	private static final void encodeDecode(FECCode encode, FECCode decode, int index[]) {
		byte[] src = new byte[KK * PACKET_SIZE];
		Util.rand.nextBytes(src);
		Buffer[] srcBufs = new Buffer[KK];
		for (int i = 0; i < srcBufs.length; i++)
			srcBufs[i] = new Buffer(src, i * PACKET_SIZE, PACKET_SIZE);

		byte[] repair = new byte[KK * PACKET_SIZE];
		Buffer[] repairBufs = new Buffer[KK];
		for (int i = 0; i < repairBufs.length; i++) {
			repairBufs[i] = new Buffer(repair, i * PACKET_SIZE, PACKET_SIZE);
		}

		encode.encode(srcBufs, repairBufs, index);
		decode.decode(repairBufs, index);

		for (int i = 0; i < src.length; i++)
			Assert.assertEquals(src[i], repair[i]);
	}

	public void testBenchmark() {
		if(!BENCHMARK) return;

		int lim = fecMath.gfSize + 1;
		FECCode maybeNative = FECCodeFactory.getDefault().createFECCode(KK, lim);
		FECCode pureCode = new PureCode(KK, lim);
		int[] index = new int[KK];

		for (int i = 0; i < KK; i++)
			index[i] = lim - i - 1;

		System.out.println("Getting ready for benchmarking");
		long t1 = System.currentTimeMillis();
		encodeDecode(maybeNative, maybeNative, index);
		long t2 = System.currentTimeMillis();
		encodeDecode(pureCode, pureCode, index);
		long t3 = System.currentTimeMillis();

		System.out.println(maybeNative);
		System.out.println(pureCode);
		long dNative = t2 - t1;
		long dPure = t3 - t2;
		System.out.println("Native code took "+dNative+"ms whereas java's code took "+dPure+"ms.");
	}

	public void testSimpleRev() {
		int lim = fecMath.gfSize + 1;
		FECCode code = FECCodeFactory.getDefault().createFECCode(KK, lim);
		FECCode code2 = new PureCode(KK, lim);
		int[] index = new int[KK];

		for (int i = 0; i < KK; i++)
			index[i] = lim - i - 1;

		encodeDecode(code, code2, index);
		encodeDecode(code2, code, index);
	}

	public void testSimple() {
		int lim = fecMath.gfSize + 1;
		FECCode code = FECCodeFactory.getDefault().createFECCode(KK, lim);
		FECCode code2 = new PureCode(KK, lim);
		int[] index = new int[KK];

		for (int i = 0; i < KK; i++)
			index[i] = KK - i;
		encodeDecode(code, code2, index);
		encodeDecode(code2, code, index);
	}

	public void testShifted() {
		int lim = fecMath.gfSize + 1;
		FECCode code = FECCodeFactory.getDefault().createFECCode(KK, lim);
		FECCode code2 = new PureCode(KK, lim);
		int[] index = new int[KK];

		int max_i0 = KK / 2;
		if (max_i0 + KK > lim)
			max_i0 = lim - KK;

		for (int s = max_i0 - 2; s <= max_i0; s++) {
			for (int i = 0; i < KK; i++)
				index[i] = i + s;
			encodeDecode(code, code2, index);
			encodeDecode(code2, code, index);
		}
	}
}
