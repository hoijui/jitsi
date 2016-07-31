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
package net.java.sip.communicator.plugin.omemo.authdialog;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.util.*;

import javax.imageio.*;
import javax.swing.*;
import javax.swing.Timer;

import net.java.omemo4j.session.*;
import net.java.sip.communicator.plugin.desktoputil.*;
import net.java.sip.communicator.plugin.omemo.*;
import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.gui.Container;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * A special {@link JMenuBar} that controls the switching of OTRv3 outgoing
 * sessions in case the remote party is logged in multiple times.
 *
 * @author Marin Dzhigarov
 *
 */
public class OTRv3OutgoingSessionSwitcher
    extends SIPCommMenuBar
    implements PluginComponent,
               ActionListener,
               ScOmemoEngineListener,
               ScOmemoKeyManagerListener
{

    private static final Logger logger
        = Logger.getLogger(OTRv3OutgoingSessionSwitcher.class);

    private final PluginComponentFactory parentFactory;

    private static final long serialVersionUID = 0L;

    private final SelectorMenu menu = new SelectorMenu();

    private ButtonGroup buttonGroup = new ButtonGroup();

    private OmemoContact contact;

    /**
     * A map used for storing each <tt>Session</tt>s corresponding <tt>JMenuItem
     * </tt>.
     */
    private final Map<Session, JMenuItem> outgoingSessions
        = new HashMap<Session, JMenuItem>();

    /**
     * An animated {@link JMenu}
     * @author Marin Dzhigarov
     *
     */
    private static class SelectorMenu
        extends SIPCommMenu
    {
        /**
         * Serial version UID.
         */
        private static final long serialVersionUID = 0L;

        Image image = OmemoActivator.resourceService.getImage(
                            "service.gui.icons.DOWN_ARROW_ICON").getImage();

        private static float alpha = 0.95f;

        private final Timer alphaChanger = new Timer(20, new ActionListener() {

                private float incrementer = -.03f;

                private int fadeCycles = 0;

                @Override
                public void actionPerformed(ActionEvent e)
                {
                    float newAlpha = alpha + incrementer;
                    if (newAlpha < 0.2f)
                    {
                        newAlpha = 0.2f;
                        incrementer = -incrementer;
                    } else if (newAlpha > 0.85f)
                    {
                        newAlpha = 0.85f;
                        incrementer = -incrementer;
                        fadeCycles++;
                    }
                    alpha = newAlpha;
                    if (fadeCycles >= 3)
                    {
                        alphaChanger.stop();
                        fadeCycles = 0;
                        alpha = 1f;
                    }
                    SelectorMenu.this.repaint();
                }
            });

        @Override
        public void paintComponent(Graphics g)
        {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setComposite(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(image, getWidth() - image.getWidth(this) - 1,
                (getHeight() - image.getHeight(this) - 1) / 2, this);

            super.paintComponent(g2d);
        }

        /**
         * Creates a fade in and out effect for this {@link JMenu}
         */
        public void fadeAnimation()
        {
            alphaChanger.stop();
            alpha = 0.85f;
            SelectorMenu.this.repaint();
            alphaChanger.start();
        }
    };

    /**
     * The OTRv3OutgoingSessionSwitcher constructor
     */
    public OTRv3OutgoingSessionSwitcher(Container container,
        PluginComponentFactory parentFactory)
    {
        this.parentFactory = parentFactory;

        setPreferredSize(new Dimension(30, 28));
        setMaximumSize(new Dimension(30, 28));
        setMinimumSize(new Dimension(30, 28));

        this.menu.setPreferredSize(new Dimension(30, 45));
        this.menu.setMaximumSize(new Dimension(30, 45));

        this.add(menu);

        this.setBorder(null);
        this.menu.setBorder(null);
        this.menu.setOpaque(false);
        this.setOpaque(false);
        this.menu.setVisible(false);

        /*
         * XXX This OmemoV3OutgoingSessionSwitcher instance cannot be added as a
         * listener to scOmemoEngine and scOmemoKeyManager without being removed
         * later on because the latter live forever. Unfortunately, the
         * dispose() method of this instance is never executed. OmemoWeakListener
         * will keep this instance as a listener of scOmemoEngine and
         * scOmemoKeyManager for as long as this instance is necessary. And this
         * instance will be strongly referenced by the JMenuItems which depict
         * it. So when the JMenuItems are gone, this instance will become
         * obsolete and OmemoWeakListener will remove it as a listener of
         * scOmemoEngine and scOmemoKeyManager.
         */
        new OmemoWeakListener<OTRv3OutgoingSessionSwitcher>(
            this,
            OmemoActivator.scOmemoEngine, OmemoActivator.scOtrKeyManager);

        try
        {
            finishedPadlockImage = new ImageIcon(ImageIO.read(
                    OmemoActivator.resourceService.getImageURL(
                        "plugin.omemo.FINISHED_ICON_BLACK_16x16")));
            verifiedLockedPadlockImage = new ImageIcon(ImageIO.read(
                    OmemoActivator.resourceService.getImageURL(
                        "plugin.omemo.ENCRYPTED_ICON_BLACK_16x16")));
            unverifiedLockedPadlockImage = new ImageIcon(ImageIO.read(
                    OmemoActivator.resourceService.getImageURL(
                        "plugin.omemo.ENCRYPTED_UNVERIFIED_ICON_BLACK_16x16")));
            unlockedPadlockImage = new ImageIcon(ImageIO.read(
                    OmemoActivator.resourceService.getImageURL(
                        "plugin.omemo.PLAINTEXT_ICON_16x16")));
        } catch (IOException e)
        {
            logger.debug("Failed to load padlock image");
        }

        buildMenu(contact);
    }

    @Override
    public int getPositionIndex()
    {
        return -1;
    }

    /**
     * Sets the current contact. Meant to be used by plugin components that
     * are interested of the current contact. The current contact is the contact
     * for the currently selected chat transport.
     *
     * @param contact the current contact
     */
    public void setCurrentContact(Contact contact)
    {
        if (this.contact != null && this.contact.contact == contact)
            return;

        this.contact =
            OmemoContactManager.getOtrContact(contact, null);
        buildMenu(this.contact);
    }

    /**
     * Sets the current meta contact. Meant to be used by plugin components that
     * are interested of the current contact. The current contact could be the
     * contact currently selected in the contact list or the contact for the
     * currently selected chat, etc. It depends on the container, where this
     * component is meant to be added.
     *
     * @param metaContact the current meta contact
     */
    public void setCurrentContact(MetaContact metaContact)
    {
        setCurrentContact((metaContact == null) ? null : metaContact
            .getDefaultContact());
    }

    /**
     * Sets the current contact. Meant to be used by plugin components that
     * are interested of the current contact. The current contact is the contact
     * for the currently selected chat transport.
     *
     * @param contact the current contact
     * @param resourceName the <tt>ContactResource</tt> name. Some components
     * may be interested in a particular ContactResource of a contact.
     */
    public void setCurrentContact(Contact contact, String resourceName)
    {
        if (resourceName == null)
        {
            this.contact =
                OmemoContactManager.getOtrContact(contact, null);
            buildMenu(this.contact);
        }
        else
        {
            for (ContactResource resource : contact.getResources())
            {
                if (resource.getResourceName().equals(resourceName))
                {
                    OmemoContact otrContact =
                        OmemoContactManager.getOtrContact(contact, resource);
                    if (this.contact == otrContact)
                        return;
                    this.contact = otrContact;
                    buildMenu(this.contact);
                }
            }
        }
    }

    @Override
    public void setCurrentContactGroup(MetaContactGroup metaGroup) {}

    @Override
    public void setCurrentAccountID(AccountID accountID) {}

    @Override
    public PluginComponentFactory getParentFactory()
    {
        return parentFactory;
    }

    /**
     * Implements ScOmemoKeyManagerListener#contactVerificationStatusChanged(
     * Contact).
     */
    public void contactVerificationStatusChanged(OmemoContact contact)
    {
        buildMenu(contact);
        if (this.menu.isVisible())
            this.menu.fadeAnimation();
    }

    /**
     * Implements ScOmemoEngineListener#contactPolicyChanged(Contact).
     */
    public void contactPolicyChanged(Contact contact) {}

    /**
     * Implements ScOmemoKeyManagerListener#globalPolicyChanged().
     */
    public void globalPolicyChanged() {}

    /**
     * Implements ScOmemoEngineListener#sessionStatusChanged(OmemoContact).
     */
    public void sessionStatusChanged(OmemoContact contact)
    {
        buildMenu(contact);
        if (this.menu.isVisible())
            this.menu.fadeAnimation();
    }

    /**
     * Implements ScOmemoEngineListener#multipleInstancesDetected(OmemoContact).
     */
    public void multipleInstancesDetected(OmemoContact contact)
    {
        buildMenu(contact);
        if (this.menu.isVisible())
            this.menu.fadeAnimation();
    }

    /**
     * Implements ScOmemoEngineListener#outgoingSessionChanged(OmemoContact).
     */
    public void outgoingSessionChanged(OmemoContact contact)
    {
        buildMenu(contact);
    }

    private ImageIcon verifiedLockedPadlockImage;

    private ImageIcon unverifiedLockedPadlockImage;

    private ImageIcon finishedPadlockImage;

    private ImageIcon unlockedPadlockImage;

    /**
     * Builds the JMenu used for switching between outgoing OTRv3 Sessions in
     * case the remote party is logged in multiple locations
     *
     * @param otrContact the contact which is logged in multiple locations
     */
    private void buildMenu(OmemoContact otrContact)
    {
        if (otrContact == null || !this.contact.equals(otrContact))
        {
            return;
        }
        menu.removeAll();
        java.util.List<Session> multipleInstances =
            OmemoActivator.scOmemoEngine.getSessionInstances(
                otrContact);

        Session outgoingSession =
            OmemoActivator.scOmemoEngine.getOutgoingSession(otrContact);
        int index = 0;
        for (Session session : multipleInstances)
        {
            index++;
            if (!outgoingSessions.containsKey(session))
            {
                JMenuItem menuItem = new JRadioButtonMenuItem();
                outgoingSessions.put(session, menuItem);
                menuItem.addActionListener(this);
            }

            JMenuItem menuItem = outgoingSessions.get(session);
            menuItem.setText("Session " + index);

            ImageIcon imageIcon = null;
            switch (session.getSessionStatus(session.getReceiverInstanceTag()))
            {
            case ENCRYPTED:
                PublicKey pubKey =
                    session.getRemotePublicKey(session.getReceiverInstanceTag());
                String fingerprint =
                    OmemoActivator.scOmemoKeyManager.
                        getFingerprintFromPublicKey(pubKey);
                imageIcon
                    = OmemoActivator.scOmemoKeyManager.isVerified(
                            otrContact.contact, fingerprint)
                        ? verifiedLockedPadlockImage
                        : unverifiedLockedPadlockImage;
                break;
            case FINISHED:
                imageIcon = finishedPadlockImage;
                break;
            case PLAINTEXT:
                imageIcon = unlockedPadlockImage;
                break;
            }
            menuItem.setIcon(imageIcon);

            menu.add(menuItem);
            SelectedObject selectedObject =
                new SelectedObject(imageIcon, session);

            buttonGroup.add(menuItem);
            menuItem.repaint();
            if (session == outgoingSession)
            {
                this.menu.setSelected(selectedObject);
                setSelected(menu.getItem(index - 1));
            }

        }
        updateEnableStatus();
        menu.repaint();
    }

    public void actionPerformed(ActionEvent e)
    {
        for (Map.Entry<Session, JMenuItem> entry : outgoingSessions.entrySet())
        {
            JMenuItem menuItem = (JRadioButtonMenuItem) e.getSource();
            if (menuItem.equals(entry.getValue()))
            {
                OmemoActivator.scOmemoEngine.setOutgoingSession(
                    contact, entry.getKey().getReceiverInstanceTag());
                break;
            }
        }
    }

    /**
     * Sets the menu visibility. The menu is visible as soon as it
     * contains two or more items. If it is empty, it is invisible.
     */
    private void updateEnableStatus()
    {
        this.menu.setEnabled(this.menu.getItemCount() > 1);
        this.menu.setVisible(true);
    }
}
