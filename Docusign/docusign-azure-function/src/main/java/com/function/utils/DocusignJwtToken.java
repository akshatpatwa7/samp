package com.function.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;

public class DocusignJwtToken  {

	private String privateKey;
	private String baseUrl;
	private String integratorKey;

	public static String OAUTH_BASEPATH = "";

	public final static String GRANT_TYPE_JWT = "urn:ietf:params:oauth:grant-type:jwt-bearer";

	public Object onCall(String docusignUserId, String oauthBasePath, String inputIntegratorKey)  {
		System.out.println("DocuSign Oauth Token generation --> Start");
		System.out.println("operating for user id :"+ docusignUserId);
		Map<String, String> jwtMap = new HashMap<String, String>();
		String DocusignUserId = docusignUserId;
		OAUTH_BASEPATH = oauthBasePath;
		this.integratorKey = inputIntegratorKey;
		
		try {
			byte[] privateKeyBytes = DocusignJwtToken.getPrivateKeyFromResourceAsStream(privateKey);

			java.util.List<String> scopes = new ArrayList<>();
			scopes.add("signature");
			scopes.add("impersonation");

			String formattedScopes = (scopes == null || scopes.size() < 1) ? "" : scopes.get(0);
			StringBuilder sb = new StringBuilder(formattedScopes);
			for (int i = 1; i < scopes.size(); i++) {
				sb.append(" " + scopes.get(i));
			}
			
			String docuSignAssertion = this.generateJWTAssertionFromByteArray(privateKeyBytes, integratorKey, DocusignUserId, 3600, sb.toString());
			
			System.out.println("docuSignAssertion generated :"+ docuSignAssertion);

			jwtMap.put("assertion", docuSignAssertion);
			jwtMap.put("grant_type", GRANT_TYPE_JWT);
		} catch (Exception e) {
			System.err.println("Exception occured while generating DocuSign Oauth Token: " + e);
		}

		return jwtMap;
	}

	public String generateJWTAssertionFromByteArray(byte[] rsaPrivateKey, String clientId,
			String userId, long expiresIn, String scopes)
			throws IllegalArgumentException, JWTCreationException, IOException {
		if (expiresIn <= 0L) {
			throw new IllegalArgumentException("expiresIn should be a non-negative value");
		}
		if (rsaPrivateKey == null || rsaPrivateKey.length == 0) {
			throw new IllegalArgumentException("rsaPrivateKey byte array is empty");
		}

		long nowTime = new Date().getTime();
		long expireTime = (new Date().getTime()) + 60000;
		Date expireDate = new Date(expireTime);
		
		RSAPrivateKey privateKey = readPrivateKeyFromByteArray(rsaPrivateKey, "RSA");
		Algorithm algorithm = Algorithm.RSA256(null, privateKey);
		JWTCreator.Builder builder = JWT.create().withIssuer(clientId).withAudience(OAUTH_BASEPATH)
				.withIssuedAt(new Date(nowTime)).withClaim("scope", scopes).withExpiresAt(expireDate);
		
		if (userId != null && userId != "") {
			builder = builder.withSubject(userId);
		}
		return builder.sign(algorithm);
	}

	/*private byte[] getPrivateKeyFromResourceAsStream(String fileName) {
		ClassLoader classLoader = getClass().getClassLoader();
		URL vUrl = classLoader.getResource(fileName);

		byte[] privateKeyBytes = null;
		try {
			privateKeyBytes = Files.readAllBytes(Paths.get(vUrl.toURI()));

		} catch (Exception e) {
			e.printStackTrace();
		}
		if (privateKeyBytes == null) {
			return null;
		}
		return privateKeyBytes;
	}
	*/

	public static byte[] getPrivateKeyFromResourceAsStream(String fileName) throws IOException {
        ClassLoader classLoader = DocusignOAuthTokenUtils.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        if (inputStream == null) {
            throw new IOException("Private key file not found: " + fileName);
        }

        return inputStream.readAllBytes();
    }






	private RSAPrivateKey readPrivateKeyFromByteArray(byte[] privateKeyBytes, String algorithm) throws IOException {
		try (PemReader reader = new PemReader(new StringReader(new String(privateKeyBytes)))) {
			PemObject pemObject = reader.readPemObject();
			byte[] bytes = pemObject.getContent();
			RSAPrivateKey privateKey = null;
			try {
				Security.addProvider(new BouncyCastleProvider());
				KeyFactory kf = KeyFactory.getInstance(algorithm, "BC");
				EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
				privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
			} catch (NoSuchAlgorithmException e) {
				System.out.println("Could not reconstruct the private key, the given algorithm could not be found.");
			} catch (InvalidKeySpecException e) {
				System.out.println("Could not reconstruct the private key");
			} catch (NoSuchProviderException e) {
				System.out.println("Could not reconstruct the private key, invalid provider.");
			}

			return privateKey;
		}
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getIntegratorKey() {
		return integratorKey;
	}

	public void setIntegratorKey(String integratorKey) {
		this.integratorKey = integratorKey;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}
}