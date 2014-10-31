package org.jberet.util;

import static org.junit.Assert.assertEquals;

import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;

import org.junit.Test;

public class BatchUtilTest {


	private static void assertPropertiesEquals(Properties expected, Properties actual) {
	
		for(Entry<Object,Object> kv : actual.entrySet()) {
			assertEquals("expected same value for key: <"+kv.getKey()+">",expected.get(kv.getKey()), kv.getValue());
		}
		
		for(Entry<Object,Object> kv : expected.entrySet()) {
			assertEquals("expected same value for key: <"+kv.getKey()+">",kv.getValue(), actual.get(kv.getKey()));
		}

	}
	
	private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static Random rnd = new Random(System.nanoTime());

	private static String randomString(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(AB.charAt(rnd.nextInt(AB.length())));
		return sb.toString();
	}

	
	@Test
	public void shouldSerializeAndDeserializeProperties() {
		
		Properties props = new Properties();
		props.setProperty("keyString", "StringValue");
		props.setProperty("keyString2", randomString(3000));
		
		Properties newProps = BatchUtil.stringToProperties(BatchUtil.propertiesToString(props));
		
		assertPropertiesEquals(props, newProps);
		
	}
}
