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
import java.util.*;

import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.protocol.*;

/**
 *
 * @author George Politis
 *
 */
public interface ScOmemoKeyManager
{

    public abstract void addListener(ScOmemoKeyManagerListener l);

    public abstract void removeListener(ScOmemoKeyManagerListener l);

    public abstract void verify(OmemoContact contact, String fingerprint);

    public abstract void unverify(OmemoContact contact, String fingerprint);

    public abstract boolean isVerified(Contact contact, String fingerprint);

    public abstract String getFingerprintFromPublicKey(PublicKey pubKey);

    public abstract List<String> getAllRemoteFingerprints(Contact contact);

    public abstract String getLocalFingerprint(AccountID account);

    public abstract byte[] getLocalFingerprintRaw(AccountID account);

    public abstract void saveFingerprint(Contact contact, String fingerprint);

    public abstract KeyPair loadKeyPair(AccountID accountID);

    public abstract void generateKeyPair(AccountID accountID);

}
