/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.plugin.omemo;

import java.security.*;
import java.security.spec.*;
import java.util.*;

import net.java.omemo4j.crypto.*;
import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.protocol.*;

/**
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class ScOmemoKeyManagerImpl
    implements ScOmemoKeyManager
{
    private final OmemoConfigurator configurator = new OmemoConfigurator();

    private final List<ScOmemoKeyManagerListener> listeners =
        new Vector<ScOmemoKeyManagerListener>();

    public void addListener(ScOmemoKeyManagerListener l)
    {
        synchronized (listeners)
        {
            if (!listeners.contains(l))
                listeners.add(l);
        }
    }

    /**
     * Gets a copy of the list of <tt>ScOmemoKeyManagerListener</tt>s registered
     * with this instance which may safely be iterated without the risk of a
     * <tt>ConcurrentModificationException</tt>.
     *
     * @return a copy of the list of <tt>ScOmemoKeyManagerListener<tt>s registered
     * with this instance which may safely be iterated without the risk of a
     * <tt>ConcurrentModificationException</tt>
     */
    private ScOmemoKeyManagerListener[] getListeners()
    {
        synchronized (listeners)
        {
            return
                listeners.toArray(
                        new ScOmemoKeyManagerListener[listeners.size()]);
        }
    }

    public void removeListener(ScOmemoKeyManagerListener l)
    {
        synchronized (listeners)
        {
            listeners.remove(l);
        }
    }

    public void verify(OmemoContact otrContact, String fingerprint)
    {
        if ((fingerprint == null) || otrContact == null)
            return;

        this.configurator.setProperty(otrContact.contact.getAddress() + fingerprint
            + ".fingerprint.verified", true);

        for (ScOmemoKeyManagerListener l : getListeners())
            l.contactVerificationStatusChanged(otrContact);
    }

    public void unverify(OmemoContact otrContact, String fingerprint)
    {
        if ((fingerprint == null) || otrContact == null)
            return;

        this.configurator.setProperty(otrContact.contact.getAddress() + fingerprint
            + ".fingerprint.verified", false);

        for (ScOmemoKeyManagerListener l : getListeners())
            l.contactVerificationStatusChanged(otrContact);
    }

    public boolean isVerified(Contact contact, String fingerprint)
    {
        if (fingerprint == null || contact == null)
            return false;

        return this.configurator.getPropertyBoolean(
            contact.getAddress() + fingerprint
                + ".fingerprint.verified", false);
    }

    public List<String> getAllRemoteFingerprints(Contact contact)
    {
        if (contact == null)
            return null;

        /*
         * The following lines are needed for backward compatibility with old
         * versions of the otr plugin. Instead of lists of fingerprints the otr
         * plugin used to store one public key for every contact in the form of
         * "userID.publicKey=..." and one boolean property in the form of
         * "userID.publicKey.verified=...". In order not to loose these old
         * properties we have to convert them to match the new format.
         */
        String userID = contact.getAddress();

        byte[] b64PubKey =
            this.configurator.getPropertyBytes(userID + ".publicKey");
        if (b64PubKey != null)
        {
            // We delete the old format property because we are going to convert
            // it in the new format
            this.configurator.removeProperty(userID + ".publicKey");

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);

            KeyFactory keyFactory;
            try
            {
                keyFactory = KeyFactory.getInstance("DSA");
                PublicKey pubKey = keyFactory.generatePublic(publicKeySpec);

                boolean isVerified =
                    this.configurator.getPropertyBoolean(userID
                        + ".publicKey.verified", false);

                // We also make sure to delete this old format property if it
                // exists.
                this.configurator.removeProperty(userID + ".publicKey.verified");

                String fingerprint = getFingerprintFromPublicKey(pubKey);

                // Now we can store the old properties in the new format.
                if (isVerified)
                    verify(OmemoContactManager.getOtrContact(contact, null), fingerprint);
                else
                    unverify(OmemoContactManager.getOtrContact(contact, null), fingerprint);

                // Finally we append the new fingerprint to out stored list of
                // fingerprints.
                this.configurator.appendProperty(
                    userID + ".fingerprints", fingerprint);
            }
            catch (NoSuchAlgorithmException e)
            {
                e.printStackTrace();
            }
            catch (InvalidKeySpecException e)
            {
                e.printStackTrace();
            }
        }

        // Now we can safely return our list of fingerprints for this contact
        // without worrying that we missed an old format property.
        return this.configurator.getAppendedProperties(
            contact.getAddress() + ".fingerprints");
    }

    public String getFingerprintFromPublicKey(PublicKey pubKey)
    {
        try
        {
            return new OmemoCryptoEngineImpl().getFingerprint(pubKey);
        }
        catch (OmemoCryptoException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public String getLocalFingerprint(AccountID account)
    {
        KeyPair keyPair = loadKeyPair(account);

        if (keyPair == null)
            return null;

        PublicKey pubKey = keyPair.getPublic();

        try
        {
            return new OmemoCryptoEngineImpl().getFingerprint(pubKey);
        }
        catch (OmemoCryptoException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getLocalFingerprintRaw(AccountID account)
    {
        KeyPair keyPair = loadKeyPair(account);

        if (keyPair == null)
            return null;

        PublicKey pubKey = keyPair.getPublic();

        try
        {
            return new OmemoCryptoEngineImpl().getFingerprintRaw(pubKey);
        }
        catch (OmemoCryptoException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public void saveFingerprint(Contact contact, String fingerprint)
    {
        if (contact == null)
            return;

        this.configurator.appendProperty(contact.getAddress() + ".fingerprints",
            fingerprint);

        this.configurator.setProperty(contact.getAddress() + fingerprint
            + ".fingerprint.verified", false);
    }

    public KeyPair loadKeyPair(AccountID account)
    {
        if (account == null)
            return null;

        String accountID = account.getAccountUniqueID();
        // Load Private Key.
        byte[] b64PrivKey =
            this.configurator.getPropertyBytes(accountID + ".privateKey");
        if (b64PrivKey == null)
            return null;

        PKCS8EncodedKeySpec privateKeySpec =
            new PKCS8EncodedKeySpec(b64PrivKey);

        // Load Public Key.
        byte[] b64PubKey =
            this.configurator.getPropertyBytes(accountID + ".publicKey");
        if (b64PubKey == null)
            return null;

        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);

        PublicKey publicKey;
        PrivateKey privateKey;

        // Generate KeyPair.
        KeyFactory keyFactory;
        try
        {
            keyFactory = KeyFactory.getInstance("DSA");
            publicKey = keyFactory.generatePublic(publicKeySpec);
            privateKey = keyFactory.generatePrivate(privateKeySpec);
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
            return null;
        }
        catch (InvalidKeySpecException e)
        {
            e.printStackTrace();
            return null;
        }

        return new KeyPair(publicKey, privateKey);
    }

    public void generateKeyPair(AccountID account)
    {
        if (account == null)
            return;

        String accountID = account.getAccountUniqueID();
        KeyPair keyPair;
        try
        {
            keyPair = KeyPairGenerator.getInstance("DSA").genKeyPair();
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
            return;
        }

        // Store Public Key.
        PublicKey pubKey = keyPair.getPublic();
        X509EncodedKeySpec x509EncodedKeySpec =
            new X509EncodedKeySpec(pubKey.getEncoded());

        this.configurator.setProperty(accountID + ".publicKey",
            x509EncodedKeySpec.getEncoded());

        // Store Private Key.
        PrivateKey privKey = keyPair.getPrivate();
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec =
            new PKCS8EncodedKeySpec(privKey.getEncoded());

        this.configurator.setProperty(accountID + ".privateKey",
            pkcs8EncodedKeySpec.getEncoded());
    }
}
