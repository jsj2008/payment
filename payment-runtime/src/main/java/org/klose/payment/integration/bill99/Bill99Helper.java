package org.klose.payment.integration.bill99;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.apache.commons.lang3.StringUtils;
import org.klose.payment.integration.bill99.constant.Bill99Constant;
import org.klose.payment.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class Bill99Helper {

	@Value("${kuaiqian.gateway.url}")
	private String gatewayURL;
	
	@Value("${kuaiqian.private.key.path}")
	private String privateKeyPath;

	@Value("${kuaiqian.private.key.name}")
	private String privateKeyName;
	
	@Value("${kuaiqian.private.key.password}")
	private String privateKeyPassword;

	@Value("${kuaiqian.public.key.path}")
	private String publicKeyPath;

	@Value("${kuaiqian.merchant.acct.id}")
	private String merchantAcctId;
	

	
	private PrivateKey privateKey;
	private PublicKey publicKey;
	
	Logger log = LoggerFactory.getLogger(Bill99Helper.class);
	
	public boolean verify(Map<String, String> params) {
		String signContent = createLinkedString(params, Bill99Constant.RETURN_PARAMETERS);
		String expectedSignature = params.get("signMsg");
		
		try {
			PublicKey publicKey = getPublicKey();

			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initVerify(publicKey);
			signature.update(signContent.getBytes());

			byte[] decodedExpectedSignature = Base64.decode(expectedSignature);
			boolean verifyResult = signature.verify(decodedExpectedSignature);

			return verifyResult;
		} catch (Exception e) {
			log.error(e.getMessage());
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public String sign(Map<String, String> params) {
		String content = createLinkedString(params, Bill99Constant.REQUEST_PARAMETERS);
		
		try {
			PrivateKey privateKey = getPrivateKey();
			
			if(privateKey == null) {
				throw new RuntimeException("Error to get private key for signature"); 
			}
			
			Signature signature = Signature.getInstance("SHA1withRSA");
			signature.initSign(privateKey);

			signature.update(content.getBytes("utf-8"));
			
			return new String(Base64.encode(signature.sign()));

		} catch (Exception e) {
			log.error(e.getMessage());
		}

		return "SIGNATURE_ERROR";
	}
	
	private PrivateKey getPrivateKey() {
		if(privateKey == null) {
			KeyStore keyStore;
			try {
				keyStore = KeyStore.getInstance("PKCS12");
				BufferedInputStream keyStream = new BufferedInputStream(
						getClass().getResourceAsStream(privateKeyPath));
		
				char[] keyPassword = privateKeyPassword.toCharArray();
				keyStore.load(keyStream, keyPassword);
			
				privateKey = (PrivateKey) keyStore.getKey(privateKeyName, keyPassword);
			} catch (Exception e) {
				log.error(e.getMessage());
			}
		}
		
		return privateKey;
	}
	
	private PublicKey getPublicKey() {
		if(publicKey == null) {
			try {
				CertificateFactory factory = CertificateFactory.getInstance("X.509");
				X509Certificate cert = (X509Certificate) factory.generateCertificate(
					getClass().getResourceAsStream(publicKeyPath));
				publicKey = cert.getPublicKey();	
			} catch (Exception e) {
				log.error(e.getMessage());
			}
		}
		
		return publicKey;
	}
	
    private String createLinkedString(Map<String, String> params, String[] validList) {
    	List<String> paramPairs = new ArrayList<String>();
    	
    	if(validList == null) {
    		validList = new String[params.size()];
    		params.keySet().toArray(validList);
    	}
		
		for(int i = 0; i < validList.length; i++) {
			String paramName = validList[i];
			String paramValue = params.get(paramName);
			if(StringUtils.isEmpty(paramValue) == false) {
				paramPairs.add(String.format("%s=%s", paramName, paramValue));
			}
		}
		
		return StringUtils.join(paramPairs, "&");
    }

}