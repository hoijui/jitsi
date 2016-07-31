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

import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

import net.java.omemo4j.*;
import net.java.omemo4j.crypto.*;
import net.java.omemo4j.session.*;
import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.plugin.omemo.authdialog.*;
import net.java.sip.communicator.service.browserlauncher.*;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

import org.osgi.framework.*;

/**
 *
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Marin Dzhigarov
 * @author Danny van Heumen
 */
public class ScOmemoEngineImpl
    implements ScOmemoEngine,
               ChatLinkClickedListener,
               ServiceListener
{
    private class ScOmemoEngineHost
        implements OmemoEngineHost
    {
        @Override
        public KeyPair getLocalKeyPair(SessionID sessionID)
        {
            AccountID accountID =
                OmemoActivator.getAccountIDByUID(sessionID.getAccountID());
            KeyPair keyPair =
                OmemoActivator.scOmemoKeyManager.loadKeyPair(accountID);
            if (keyPair == null)
                OmemoActivator.scOmemoKeyManager.generateKeyPair(accountID);

            return OmemoActivator.scOmemoKeyManager.loadKeyPair(accountID);
        }

        @Override
        public OmemoPolicy getSessionPolicy(SessionID sessionID)
        {
            return getContactPolicy(getOmemoContact(sessionID).contact);
        }

        @Override
        public void injectMessage(SessionID sessionID, String messageText)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            Contact contact = otrContact.contact;
            ContactResource resource = null;

            if (contact.supportResources())
            {
                Collection<ContactResource> resources = contact.getResources();
                if (resources != null)
                {
                    for (ContactResource r : resources)
                    {
                        if (r.equals(otrContact.resource))
                        {
                            resource = r;
                            break;
                        }
                    }
                }
            }

            OperationSetBasicInstantMessaging imOpSet
                = contact
                    .getProtocolProvider()
                        .getOperationSet(
                                OperationSetBasicInstantMessaging.class);

            // This is a dirty way of detecting whether the injected message
            // contains HTML markup. If this is the case then we should create
            // the message with the appropriate content type so that the remote
            // party can properly display the HTML.
            // When otr4j injects QueryMessages it calls
            // OmemoEngineHost.getFallbackMessage() which is currently the only
            // host method that uses HTML so we can simply check if the injected
            // message contains the string that getFallbackMessage() returns.
            String otrHtmlFallbackMessage
                = "<a href=\"http://en.wikipedia.org/wiki/Off-the-Record_Messaging\">";
            String contentType
                = messageText.contains(otrHtmlFallbackMessage)
                    ? OperationSetBasicInstantMessaging.HTML_MIME_TYPE
                    : OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE;
            Message message
                = imOpSet.createMessage(
                        messageText,
                        contentType,
                        OperationSetBasicInstantMessaging.DEFAULT_MIME_ENCODING,
                        null);

            injectedMessageUIDs.add(message.getMessageUID());
            imOpSet.sendInstantMessage(contact, resource, message);
        }

        @Override
        public void showError(SessionID sessionID, String err)
        {
            ScOmemoEngineImpl.this.showError(sessionID, err);
        }

        public void showWarning(SessionID sessionID, String warn)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.SYSTEM_MESSAGE, warn,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
        }

        @Override
        public void unreadableMessageReceived(SessionID sessionID)
            throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            String resourceName = otrContact.resource != null ?
                "/" + otrContact.resource.getResourceName() : "";

            Contact contact = otrContact.contact;
            String error =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.unreadablemsgreceived",
                    new String[]
                        {contact.getDisplayName() + resourceName});
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.ERROR_MESSAGE, error,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
        }

        @Override
        public void unencryptedMessageReceived(SessionID sessionID, String msg)
            throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            String warn =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.unencryptedmsgreceived");
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.SYSTEM_MESSAGE, warn,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
        }

        @Override
        public void smpError(SessionID sessionID, int tlvType, boolean cheated)
            throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            logger.debug("SMP error occurred"
                        + ". Contact: " + contact.getDisplayName()
                        + ". TLV type: " + tlvType
                        + ". Cheated: " + cheated);

            String error =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.smperror");
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.ERROR_MESSAGE, error,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.setProgressFail();
            progressDialog.setVisible(true);
        }

        @Override
        public void smpAborted(SessionID sessionID) throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            Session session = otrEngine.getSession(sessionID);
            if (session.isSmpInProgress())
            {
                String warn =
                    OmemoActivator.resourceService.getI18NString(
                        "plugin.omemo.activator.smpaborted",
                        new String[] {contact.getDisplayName()});
                OmemoActivator.uiService.getChat(contact).addMessage(
                    contact.getDisplayName(), new Date(),
                    Chat.SYSTEM_MESSAGE, warn,
                    OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);

                SmpProgressDialog progressDialog =
                    progressDialogMap.get(otrContact);
                if (progressDialog == null)
                {
                    progressDialog = new SmpProgressDialog(contact);
                    progressDialogMap.put(otrContact, progressDialog);
                }

                progressDialog.setProgressFail();
                progressDialog.setVisible(true);
            }
        }

        @Override
        public void finishedSessionMessage(SessionID sessionID, String msgText)
            throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            String resourceName = otrContact.resource != null ?
                "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String error =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.sessionfinishederror",
                    new String[]
                        {msgText, contact.getDisplayName() + resourceName});
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.ERROR_MESSAGE, error,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
        }

        @Override
        public void requireEncryptedMessage(SessionID sessionID, String msgText)
            throws OmemoException
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

                Contact contact = otrContact.contact;
            String error =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.requireencryption",
                    new String[]
                        {msgText});
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(), new Date(),
                Chat.ERROR_MESSAGE, error,
                OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
        }

        @Override
        public byte[] getLocalFingerprintRaw(SessionID sessionID)
        {
            AccountID accountID =
                OmemoActivator.getAccountIDByUID(sessionID.getAccountID());
            return
                OmemoActivator.scOmemoKeyManager.getLocalFingerprintRaw(accountID);
        }

        @Override
        public void askForSecret(
            SessionID sessionID, InstanceTag receiverTag, String question)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            SmpAuthenticateBuddyDialog dialog =
                new SmpAuthenticateBuddyDialog(
                    otrContact, receiverTag, question);
            dialog.setVisible(true);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.init();
            progressDialog.setVisible(true);
        }

        @Override
        public void verify(
            SessionID sessionID, String fingerprint, boolean approved)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OmemoActivator.scOmemoKeyManager.verify(otrContact, fingerprint);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.setProgressSuccess();
            progressDialog.setVisible(true);
        }

        @Override
        public void unverify(SessionID sessionID, String fingerprint)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OmemoActivator.scOmemoKeyManager.unverify(otrContact, fingerprint);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.setProgressFail();
            progressDialog.setVisible(true);
        }

        @Override
        public String getReplyForUnreadableMessage(SessionID sessionID)
        {
            AccountID accountID =
                OmemoActivator.getAccountIDByUID(sessionID.getAccountID());

            return OmemoActivator.resourceService.getI18NString(
                "plugin.omemo.activator.unreadablemsgreply",
                new String[] {accountID.getDisplayName(),
                              accountID.getDisplayName()});
        }

        @Override
        public String getFallbackMessage(SessionID sessionID)
        {
            AccountID accountID =
                OmemoActivator.getAccountIDByUID(sessionID.getAccountID());

            return OmemoActivator.resourceService.getI18NString(
                "plugin.omemo.activator.fallbackmessage",
                new String[] {accountID.getDisplayName()});
        }

        @Override
        public void multipleInstancesDetected(SessionID sessionID)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            String resourceName = otrContact.resource != null ?
                "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String message =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.multipleinstancesdetected",
                    new String[]
                        {contact.getDisplayName() + resourceName});
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(),
                new Date(), Chat.SYSTEM_MESSAGE,
                message,
                OperationSetBasicInstantMessaging.HTML_MIME_TYPE);
        }

        @Override
        public void messageFromAnotherInstanceReceived(SessionID sessionID)
        {
            OmemoContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            String resourceName = otrContact.resource != null ?
                "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String message =
                OmemoActivator.resourceService.getI18NString(
                    "plugin.omemo.activator.msgfromanotherinstance",
                    new String[]
                        {contact.getDisplayName() + resourceName});
            OmemoActivator.uiService.getChat(contact).addMessage(
                contact.getDisplayName(),
                new Date(), Chat.SYSTEM_MESSAGE,
                message,
                OperationSetBasicInstantMessaging.HTML_MIME_TYPE);
        }

        /**
         * Provide fragmenter instructions according to the Instant Messaging
         * transport channel of the contact's protocol.
         */
        @Override
        public FragmenterInstructions getFragmenterInstructions(
            final SessionID sessionID)
        {
            final OmemoContact otrContact = getOtrContact(sessionID);
            final OperationSetBasicInstantMessagingTransport transport =
                otrContact.contact.getProtocolProvider().getOperationSet(
                    OperationSetBasicInstantMessagingTransport.class);
            if (transport == null)
            {
                // There is no operation set for querying transport parameters.
                // Assuming transport capabilities are unlimited.
                if (logger.isDebugEnabled())
                {
                    logger.debug("No implementation of "
                        + "BasicInstantMessagingTransport available. Assuming "
                        + "OTR defaults for OTR fragmentation instructions.");
                }
                return null;
            }
            int messageSize = transport.getMaxMessageSize(otrContact.contact);
            if (messageSize
                == OperationSetBasicInstantMessagingTransport.UNLIMITED)
            {
                messageSize = FragmenterInstructions.UNLIMITED;
            }
            int numberOfMessages =
                transport.getMaxNumberOfMessages(otrContact.contact);
            if (numberOfMessages
                == OperationSetBasicInstantMessagingTransport.UNLIMITED)
            {
                numberOfMessages = FragmenterInstructions.UNLIMITED;
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("OTR fragmentation instructions for sending a "
                    + "message to " + otrContact.contact.getDisplayName()
                    + " (" + otrContact.contact.getAddress()
                    + "). Maximum number of " + "messages: " + numberOfMessages
                    + ", maximum message size: " + messageSize);
            }
            return new FragmenterInstructions(numberOfMessages, messageSize);
        }
    }

    /**
     * The max timeout period elapsed prior to establishing a TIMED_OUT session.
     */
    private static final int SESSION_TIMEOUT =
        OmemoActivator.configService.getInt(
            "net.java.sip.communicator.plugin.omemo.SESSION_STATUS_TIMEOUT",
            30000);

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's
     * ScSessionStatus (to TIMED_OUT) after a period of time.
     */
    private ScSessionStatusScheduler scheduler = new ScSessionStatusScheduler();

    /**
     * This mapping is used for taking care of keeping SessionStatus and
     * ScSessionStatus in sync for every Session object.
     */
    private Map<SessionID, ScSessionStatus> scSessionStatusMap =
        new ConcurrentHashMap<SessionID, ScSessionStatus>();

    private static final Map<ScSessionID, OmemoContact> contactsMap =
        new Hashtable<ScSessionID, OmemoContact>();

    private static final Map<OmemoContact, SmpProgressDialog> progressDialogMap =
        new ConcurrentHashMap<OmemoContact, SmpProgressDialog>();

    public static OmemoContact getOtrContact(SessionID sessionID)
    {
        return contactsMap.get(new ScSessionID(sessionID));
    }

    /**
     * Returns the <tt>ScSessionID</tt> for given <tt>UUID</tt>.
     * @param guid the <tt>UUID</tt> identifying <tt>ScSessionID</tt>.
     * @return the <tt>ScSessionID</tt> for given <tt>UUID</tt> or <tt>null</tt>
     *         if no matching session found.
     */
    public static ScSessionID getScSessionForGuid(UUID guid)
    {
        for(ScSessionID scSessionID : contactsMap.keySet())
        {
            if(scSessionID.getGUID().equals(guid))
            {
                return scSessionID;
            }
        }
        return null;
    }

    public static SessionID getSessionID(OmemoContact otrContact)
    {
        ProtocolProviderService pps = otrContact.contact.getProtocolProvider();
        String resourceName = otrContact.resource != null ?
            "/" + otrContact.resource.getResourceName() : "";
        SessionID sessionID
            = new SessionID(
                    pps.getAccountID().getAccountUniqueID(),
                    otrContact.contact.getAddress() + resourceName,
                    pps.getProtocolName());

        synchronized (contactsMap)
        {
            if(contactsMap.containsKey(new ScSessionID(sessionID)))
                return sessionID;

            ScSessionID scSessionID = new ScSessionID(sessionID);

            contactsMap.put(scSessionID, otrContact);
        }

        return sessionID;
    }

    private final OmemoConfigurator configurator = new OmemoConfigurator();

    private final List<String> injectedMessageUIDs = new Vector<String>();

    private final List<ScOmemoEngineListener> listeners =
        new Vector<ScOmemoEngineListener>();

    /**
     * The logger
     */
    private final Logger logger = Logger.getLogger(ScOmemoEngineImpl.class);

    private final OmemoEngineHost otrEngineHost = new ScOmemoEngineHost();

    private final OmemoSessionManager otrEngine;

    public ScOmemoEngineImpl()
    {
        otrEngine = new OmemoSessionManagerImpl(otrEngineHost);

        // Clears the map after previous instance
        // This is required because of OSGi restarts in the same VM on Android
        contactsMap.clear();
        scSessionStatusMap.clear();

        this.omemoEngine.addOmemoEngineListener(new OmemoEngineListener()
        {
            @Override
            public void sessionStatusChanged(SessionID sessionID)
            {
                OmemoContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                String resourceName = otrContact.resource != null ?
                    "/" + otrContact.resource.getResourceName() : "";
                Contact contact = otrContact.contact;
                // Cancels any scheduled tasks that will change the
                // ScSessionStatus for this Contact
                scheduler.cancel(otrContact);

                ScSessionStatus scSessionStatus = getSessionStatus(otrContact);
                String message = "";
                final Session session = otrEngine.getSession(sessionID);
                switch (session.getSessionStatus())
                {
                case ENCRYPTED:
                    scSessionStatus = ScSessionStatus.ENCRYPTED;
                    scSessionStatusMap.put(sessionID, scSessionStatus);
                    PublicKey remotePubKey = session.getRemotePublicKey();

                    String remoteFingerprint = null;
                    try
                    {
                        remoteFingerprint =
                            new OmemoCryptoEngineImpl().
                                getFingerprint(remotePubKey);
                    }
                    catch (OmemoCryptoException e)
                    {
                        logger.debug(
                            "Could not get the fingerprint from the "
                            + "public key of contact: " + contact);
                    }

                    List<String> allFingerprintsOfContact =
                        OmemoActivator.scOmemoKeyManager.
                            getAllRemoteFingerprints(contact);
                    if (allFingerprintsOfContact != null)
                    {
                        if (!allFingerprintsOfContact.contains(
                                remoteFingerprint))
                        {
                            OmemoActivator.scOmemoKeyManager.saveFingerprint(
                                contact, remoteFingerprint);
                        }
                    }

                    if (!OmemoActivator.scOmemoKeyManager.isVerified(
                            contact, remoteFingerprint))
                    {
                        OmemoActivator.scOmemoKeyManager.unverify(
                            otrContact, remoteFingerprint);
                        UUID sessionGuid = null;
                        for(ScSessionID scSessionID : contactsMap.keySet())
                        {
                            if(scSessionID.getSessionID().equals(sessionID))
                            {
                                sessionGuid = scSessionID.getGUID();
                                break;
                            }
                        }

                        OmemoActivator.uiService.getChat(contact)
                            .addChatLinkClickedListener(ScOmemoEngineImpl.this);

                        String unverifiedSessionWarning
                            = OmemoActivator.resourceService.getI18NString(
                                    "plugin.omemo.activator"
                                        + ".unverifiedsessionwarning",
                                    new String[]
                                    {
                                        contact.getDisplayName() + resourceName,
                                        this.getClass().getName(),
                                        "AUTHENTIFICATION",
                                        sessionGuid.toString()
                                    });
                        OmemoActivator.uiService.getChat(contact).addMessage(
                            contact.getDisplayName(),
                            new Date(), Chat.SYSTEM_MESSAGE,
                            unverifiedSessionWarning,
                            OperationSetBasicInstantMessaging.HTML_MIME_TYPE);

                    }

                    // show info whether history is on or off
                    String otrAndHistoryMessage;
                    if(!OmemoActivator.getMessageHistoryService()
                        .isHistoryLoggingEnabled() ||
                        !isHistoryLoggingEnabled(contact))
                    {
                        otrAndHistoryMessage =
                            OmemoActivator.resourceService.getI18NString(
                                "plugin.omemo.activator.historyoff",
                                new String[]{
                                    OmemoActivator.resourceService
                                        .getSettingsString(
                                            "service.gui.APPLICATION_NAME"),
                                    this.getClass().getName(),
                                    "showHistoryPopupMenu"
                                });
                    }
                    else
                    {
                        otrAndHistoryMessage =
                            OmemoActivator.resourceService.getI18NString(
                                "plugin.omemo.activator.historyon",
                                new String[]{
                                    OmemoActivator.resourceService
                                        .getSettingsString(
                                            "service.gui.APPLICATION_NAME"),
                                    this.getClass().getName(),
                                    "showHistoryPopupMenu"
                                });
                    }
                    OmemoActivator.uiService.getChat(contact).addMessage(
                        contact.getDisplayName(),
                        new Date(), Chat.SYSTEM_MESSAGE,
                        otrAndHistoryMessage,
                        OperationSetBasicInstantMessaging.HTML_MIME_TYPE);

                    message =
                        OmemoActivator.resourceService.getI18NString(
                            "plugin.omemo.activator.multipleinstancesdetected",
                            new String[]
                                {contact.getDisplayName()});

                    if (contact.supportResources()
                        && contact.getResources() != null
                        && contact.getResources().size() > 1)
                        OmemoActivator.uiService.getChat(contact).addMessage(
                            contact.getDisplayName(),
                            new Date(), Chat.SYSTEM_MESSAGE,
                            message,
                            OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);

                    message
                        = OmemoActivator.resourceService.getI18NString(
                                OmemoActivator.scOmemoKeyManager.isVerified(
                                    contact, remoteFingerprint)
                                    ? "plugin.omemo.activator.sessionstared"
                                    : "plugin.omemo.activator"
                                        + ".unverifiedsessionstared",
                                new String[]
                                    {contact.getDisplayName() + resourceName});

                    break;
                case FINISHED:
                    scSessionStatus = ScSessionStatus.FINISHED;
                    scSessionStatusMap.put(sessionID, scSessionStatus);
                    message =
                        OmemoActivator.resourceService.getI18NString(
                            "plugin.omemo.activator.sessionfinished",
                            new String[]
                                {contact.getDisplayName() + resourceName});
                    break;
                case PLAINTEXT:
                    scSessionStatus = ScSessionStatus.PLAINTEXT;
                    scSessionStatusMap.put(sessionID, scSessionStatus);
                    message =
                        OmemoActivator.resourceService.getI18NString(
                            "plugin.omemo.activator.sessionlost", new String[]
                                {contact.getDisplayName() + resourceName});
                    break;
                }

                OmemoActivator.uiService.getChat(contact).addMessage(
                    contact.getDisplayName(), new Date(),
                    Chat.SYSTEM_MESSAGE, message,
                    OperationSetBasicInstantMessaging.HTML_MIME_TYPE);

                for (ScOmemoEngineListener l : getListeners())
                    l.sessionStatusChanged(otrContact);
            }

            @Override
            public void multipleInstancesDetected(SessionID sessionID)
            {
                OmemoContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                for (ScOmemoEngineListener l : getListeners())
                    l.multipleInstancesDetected(otrContact);
            }

            @Override
            public void outgoingSessionChanged(SessionID sessionID)
            {
                OmemoContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                for (ScOmemoEngineListener l : getListeners())
                    l.outgoingSessionChanged(otrContact);
            }
        });
    }

    /**
     * Checks whether history is enabled for the metacontact containing
     * the <tt>contact</tt>.
     * @param contact the contact to check.
     * @return whether chat logging is enabled while chatting
     * with <tt>contact</tt>.
     */
    private boolean isHistoryLoggingEnabled(Contact contact)
    {
        MetaContact metaContact = OmemoActivator
            .getContactListService().findMetaContactByContact(contact);
        if(metaContact != null)
            return OmemoActivator.getMessageHistoryService()
                .isHistoryLoggingEnabled(metaContact.getMetaUID());
        else
            return true;
    }

    @Override
    public void addListener(ScOmemoEngineListener l)
    {
        synchronized (listeners)
        {
            if (!listeners.contains(l))
                listeners.add(l);
        }
    }

    @Override
    public void chatLinkClicked(URI url)
    {
        String action = url.getPath();
        if(action.equals("/AUTHENTIFICATION"))
        {
            UUID guid = UUID.fromString(url.getQuery());

            if(guid == null)
                throw new RuntimeException(
                        "No UUID found in OTR authenticate URL");

            // Looks for registered action handler
            OmemoActionHandler actionHandler
                    = ServiceUtils.getService(
                            OmemoActivator.bundleContext,
                            OmemoActionHandler.class);

            if(actionHandler != null)
            {
                actionHandler.onAuthenticateLinkClicked(guid);
            }
            else
            {
                logger.error("No OmemoActionHandler registered");
            }
        }
    }

    @Override
    public void endSession(OmemoContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        try
        {
            setSessionStatus(otrContact, ScSessionStatus.PLAINTEXT);

            otrEngine.getSession(sessionID).endSession();
        }
        catch (OmemoException e)
        {
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public OmemoPolicy getContactPolicy(Contact contact)
    {
        ProtocolProviderService pps = contact.getProtocolProvider();
        SessionID sessionID
            = new SessionID(
                pps.getAccountID().getAccountUniqueID(),
                contact.getAddress(),
                pps.getProtocolName());
        int policy =
            this.configurator.getPropertyInt(sessionID + "contact_policy",
                -1);
        if (policy < 0)
            return getGlobalPolicy();
        else
            return new OmemoPolicyImpl(policy);
    }

    @Override
    public OmemoPolicy getGlobalPolicy()
    {
        /*
         * SEND_WHITESPACE_TAG bit will be lowered until we stabilize the OTR.
         */
        int defaultScOmemoPolicy =
            OmemoPolicy.OTRL_POLICY_DEFAULT & ~OtrPolicy.SEND_WHITESPACE_TAG;
        return new OmemoPolicyImpl(this.configurator.getPropertyInt(
            "GLOBAL_POLICY", defaultScOmemoPolicy));
    }

    /**
     * Gets a copy of the list of <tt>ScOmemoEngineListener</tt>s registered with
     * this instance which may safely be iterated without the risk of a
     * <tt>ConcurrentModificationException</tt>.
     *
     * @return a copy of the list of <tt>ScOmemoEngineListener<tt>s registered
     * with this instance which may safely be iterated without the risk of a
     * <tt>ConcurrentModificationException</tt>
     */
    private ScOmemoEngineListener[] getListeners()
    {
        synchronized (listeners)
        {
            return listeners.toArray(new ScOmemoEngineListener[listeners.size()]);
        }
    }

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's
     * ScSessionStatus after a period of time.
     *
     * @author Marin Dzhigarov
     */
    private class ScSessionStatusScheduler
    {
        private final Timer timer = new Timer();

        private final Map<OmemoContact, TimerTask> tasks =
            new ConcurrentHashMap<OmemoContact, TimerTask>();

        public void scheduleScSessionStatusChange(
            final OmemoContact otrContact, final ScSessionStatus status)
        {
            cancel(otrContact);

            TimerTask task
                = new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        setSessionStatus(otrContact, status);
                    }
                };
            timer.schedule(task, SESSION_TIMEOUT);
            tasks.put(otrContact, task);
        }

        public void cancel(final OmemoContact otrContact)
        {
            TimerTask task = tasks.get(otrContact);
            if (task != null)
                task.cancel();
            tasks.remove(otrContact);
        }

        public void serviceChanged(ServiceEvent ev)
        {
            Object service
                = OmemoActivator.bundleContext.getService(
                    ev.getServiceReference());

            if (!(service instanceof ProtocolProviderService))
                return;

            if (ev.getType() == ServiceEvent.UNREGISTERING)
            {
                ProtocolProviderService provider
                    = (ProtocolProviderService) service;

                Iterator<OmemoContact> i = tasks.keySet().iterator();

                while (i.hasNext())
                {
                    OmemoContact otrContact = i.next();
                    if (provider.equals(
                        otrContact.contact.getProtocolProvider()))
                    {
                        cancel(otrContact);
                        i.remove();
                    }
                }
            }
        }
    }

    private void setSessionStatus(OmemoContact contact, ScSessionStatus status)
    {
        scSessionStatusMap.put(getSessionID(contact), status);
        scheduler.cancel(contact);
        for (ScOmemoEngineListener l : getListeners())
            l.sessionStatusChanged(contact);
    }

    @Override
    public ScSessionStatus getSessionStatus(OmemoContact contact)
    {
        SessionID sessionID = getSessionID(contact);
        SessionStatus sessionStatus = otrEngine.getSession(sessionID).getSessionStatus();
        ScSessionStatus scSessionStatus = null;
        if (!scSessionStatusMap.containsKey(sessionID))
        {
            switch (sessionStatus)
            {
            case PLAINTEXT:
                scSessionStatus = ScSessionStatus.PLAINTEXT;
                break;
            case ENCRYPTED:
                scSessionStatus = ScSessionStatus.ENCRYPTED;
                break;
            case FINISHED:
                scSessionStatus = ScSessionStatus.FINISHED;
                break;
            }
            scSessionStatusMap.put(sessionID, scSessionStatus);
        }
        return scSessionStatusMap.get(sessionID);
    }

    @Override
    public boolean isMessageUIDInjected(String mUID)
    {
        return injectedMessageUIDs.contains(mUID);
    }

    @Override
    public void launchHelp()
    {
        ServiceReference ref =
            OmemoActivator.bundleContext
                .getServiceReference(BrowserLauncherService.class.getName());

        if (ref == null)
            return;

        BrowserLauncherService service =
            (BrowserLauncherService) OmemoActivator.bundleContext.getService(ref);

        service.openURL(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.authbuddydialog.HELP_URI"));
    }

    @Override
    public void refreshSession(OmemoContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        try
        {
            otrEngine.getSession(sessionID).refreshSession();
        }
        catch (OmemoException e)
        {
            logger.error("Error refreshing session", e);
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public void removeListener(ScOmemoEngineListener l)
    {
        synchronized (listeners)
        {
            listeners.remove(l);
        }
    }

    /**
     * Cleans the contactsMap when <tt>ProtocolProviderService</tt>
     * gets unregistered.
     */
    @Override
    public void serviceChanged(ServiceEvent ev)
    {
        Object service
            = OmemoActivator.bundleContext.getService(ev.getServiceReference());

        if (!(service instanceof ProtocolProviderService))
            return;

        if (ev.getType() == ServiceEvent.UNREGISTERING)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Unregistering a ProtocolProviderService, cleaning"
                            + " OTR's ScSessionID to Contact map.");
                logger.debug(
                        "Unregistering a ProtocolProviderService, cleaning"
                            + " OTR's Contact to SpmProgressDialog map.");
            }

            ProtocolProviderService provider
                = (ProtocolProviderService) service;

            synchronized(contactsMap)
            {
                Iterator<OmemoContact> i = contactsMap.values().iterator();

                while (i.hasNext())
                {
                    OmemoContact otrContact = i.next();
                    if (provider.equals(
                        otrContact.contact.getProtocolProvider()))
                    {
                        scSessionStatusMap.remove(getSessionID(otrContact));
                        i.remove();
                    }
                }
            }

            Iterator<OmemoContact> i = progressDialogMap.keySet().iterator();

            while (i.hasNext())
            {
                if (provider.equals(i.next().contact.getProtocolProvider()))
                    i.remove();
            }
            scheduler.serviceChanged(ev);
        }
    }

    @Override
    public void setContactPolicy(Contact contact, OmemoPolicy policy)
    {
        ProtocolProviderService pps = contact.getProtocolProvider();
        SessionID sessionID
            = new SessionID(
                pps.getAccountID().getAccountUniqueID(),
                contact.getAddress(),
                pps.getProtocolName());

        String propertyID = sessionID + "contact_policy";
        if (policy == null)
            this.configurator.removeProperty(propertyID);
        else
            this.configurator.setProperty(propertyID, policy.getPolicy());

        for (ScOmemoEngineListener l : getListeners())
            l.contactPolicyChanged(contact);
    }

    @Override
    public void setGlobalPolicy(OmemoPolicy policy)
    {
        if (policy == null)
            this.configurator.removeProperty("GLOBAL_POLICY");
        else
            this.configurator.setProperty("GLOBAL_POLICY", policy.getPolicy());

        for (ScOmemoEngineListener l : getListeners())
            l.globalPolicyChanged();
    }

    public void showError(SessionID sessionID, String err)
    {
        OmemoContact otrContact = getOtrContact(sessionID);
        if (otrContact == null)
            return;

        Contact contact = otrContact.contact;
        OmemoActivator.uiService.getChat(contact).addMessage(
            contact.getDisplayName(), new Date(),
            Chat.ERROR_MESSAGE, err,
            OperationSetBasicInstantMessaging.DEFAULT_MIME_TYPE);
    }

    @Override
    public void startSession(OmemoContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);

        ScSessionStatus scSessionStatus = getSessionStatus(otrContact);
        scSessionStatus = ScSessionStatus.LOADING;
        scSessionStatusMap.put(sessionID, scSessionStatus);
        for (ScOmemoEngineListener l : getListeners())
        {
            l.sessionStatusChanged(otrContact);
        }

        scheduler.scheduleScSessionStatusChange(
            otrContact, ScSessionStatus.TIMED_OUT);

        try
        {
            otrEngine.getSession(sessionID).startSession();
        }
        catch (OmemoException e)
        {
            logger.error("Error starting session", e);
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public String transformReceiving(OmemoContact otrContact, String msgText)
    {
        SessionID sessionID = getSessionID(otrContact);
        try
        {
            return otrEngine.getSession(sessionID).transformReceiving(msgText);
        }
        catch (OmemoException e)
        {
            logger.error("Error receiving the message", e);
            showError(sessionID, e.getMessage());
            return null;
        }
    }

    @Override
    public String[] transformSending(OmemoContact otrContact, String msgText)
    {
        SessionID sessionID = getSessionID(otrContact);
        try
        {
            return otrEngine.getSession(sessionID).transformSending(msgText);
        }
        catch (OmemoException e)
        {
            logger.error("Error transforming the message", e);
            showError(sessionID, e.getMessage());
            return null;
        }
    }

    private Session getSession(OmemoContact contact)
    {
        SessionID sessionID = getSessionID(contact);
        return otrEngine.getSession(sessionID);
    }

    @Override
    public void initSmp(OmemoContact otrContact, String question, String secret)
    {
        Session session = getSession(otrContact);
        try
        {
            session.initSmp(question, secret);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.init();
            progressDialog.setVisible(true);
        }
        catch (OmemoException e)
        {
            logger.error("Error initializing SMP session with contact "
                         + otrContact.contact.getDisplayName(), e);
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public void respondSmp( OmemoContact otrContact,
                            InstanceTag receiverTag,
                            String question,
                            String secret)
    {
        Session session = getSession(otrContact);
        try
        {
            session.respondSmp(receiverTag, question, secret);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.incrementProgress();
            progressDialog.setVisible(true);
        }
        catch (OmemoException e)
        {
            logger.error(
                "Error occured when sending SMP response to contact "
                + otrContact.contact.getDisplayName(), e);
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public void abortSmp(OmemoContact otrContact)
    {
        Session session = getSession(otrContact);
        try
        {
            session.abortSmp();

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null)
            {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }

            progressDialog.dispose();
        }
        catch (OmemoException e)
        {
            logger.error("Error aborting SMP session with contact "
                         + otrContact.contact.getDisplayName(), e);
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public PublicKey getRemotePublicKey(OmemoContact otrContact)
    {
        if (otrContact == null)
            return null;

        Session session = getSession(otrContact);

        return session.getRemotePublicKey();
    }

    @Override
    public List<Session> getSessionInstances(OmemoContact otrContact)
    {
        if (otrContact == null)
            return Collections.emptyList();
        return getSession(otrContact).getInstances();
    }

    @Override
    public boolean setOutgoingSession(OmemoContact contact, InstanceTag tag)
    {
        if (contact == null)
            return false;

        Session session = getSession(contact);

        scSessionStatusMap.remove(session.getSessionID());
        return session.setOutgoingInstance(tag);
    }

    @Override
    public Session getOutgoingSession(OmemoContact contact)
    {
        if (contact == null)
            return null;

        SessionID sessionID = getSessionID(contact);

        return otrEngine.getSession(sessionID).getOutgoingInstance();
    }
}
