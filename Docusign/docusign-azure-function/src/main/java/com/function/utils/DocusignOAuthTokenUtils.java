package com.function.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;

public class DocusignOAuthTokenUtils {

    private static final String PRIVATE_KEY_FILE = "RSAPrivateKey.txt";

    public static byte[] getPrivateKeyFromResourceAsStream() throws IOException {
        ClassLoader classLoader = DocusignOAuthTokenUtils.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(PRIVATE_KEY_FILE);

        if (inputStream == null) {
            throw new IOException("Private key file not found: " + PRIVATE_KEY_FILE);
        }

        return inputStream.readAllBytes();
    }

    public static String generateJWTAssertion(byte[] rsaPrivateKey, String clientId, String userId,
                                               long expiresIn, String scopes, String oauthBasePath)
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
        JWTCreator.Builder builder = JWT.create()
                .withIssuer(clientId)
                .withAudience(oauthBasePath)
                .withIssuedAt(new Date(nowTime))
                .withClaim("scope", scopes)
                .withExpiresAt(expireDate);

        if (userId != null && userId != "") {
            builder = builder.withSubject(userId);
        }

        return builder.sign(algorithm);
    }

    private static RSAPrivateKey readPrivateKeyFromByteArray(byte[] privateKeyBytes, String algorithm) throws IOException {
        PemReader reader = new PemReader(new StringReader(new String(privateKeyBytes)));
        try {
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
        } finally {
            reader.close();
        }
    }
}
