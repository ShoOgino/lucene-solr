/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Lucene" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Lucene", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package de.lanlab.larm.gui;

/*
    A basic extension of the java.awt.Dialog class
 */

import java.awt.*;

public class AboutDialog extends Dialog {

    public AboutDialog(Frame parent, boolean modal)
    {
        super(parent, modal);

        // This code is automatically generated by Visual Cafe when you add
        // components to the visual environment. It instantiates and initializes
        // the components. To modify the code, only use code syntax that matches
        // what Visual Cafe can generate, or Visual Cafe may be unable to back
        // parse your Java file into its visual environment.

        //{{INIT_CONTROLS
        setLayout(null);
        setSize(249,150);
        setVisible(false);
        label1.setText("LARM - LANLab Retrieval Machine");
        add(label1);
        label1.setBounds(12,12,228,24);
        okButton.setLabel("OK");
        add(okButton);
        okButton.setBounds(95,85,66,27);
        label2.setText("(C) 2000 Clemens Marschner");
        add(label2);
        label2.setBounds(12,36,228,24);
        setTitle("AWT-Anwendung - Info");
        //}}

        //{{REGISTER_LISTENERS
        SymWindow aSymWindow = new SymWindow();
        this.addWindowListener(aSymWindow);
        SymAction lSymAction = new SymAction();
        okButton.addActionListener(lSymAction);
        //}}

    }

    public AboutDialog(Frame parent, String title, boolean modal)
    {
        this(parent, modal);
        setTitle(title);
    }

    public void addNotify()
    {
        // Record the size of the window prior to calling parents addNotify.
                Dimension d = getSize();

        super.addNotify();

        // Only do this once.
        if (fComponentsAdjusted)
            return;

        // Adjust components according to the insets
        Insets insets = getInsets();
        setSize(insets.left + insets.right + d.width, insets.top + insets.bottom + d.height);
        Component components[] = getComponents();
        for (int i = 0; i < components.length; i++)
        {
            Point p = components[i].getLocation();
            p.translate(insets.left, insets.top);
            components[i].setLocation(p);
        }

        // Used for addNotify check.
        fComponentsAdjusted = true;
    }

    public void setVisible(boolean b)
    {
        if (b)
        {
            Rectangle bounds = getParent().getBounds();
            Rectangle abounds = getBounds();

            setLocation(bounds.x + (bounds.width - abounds.width)/ 2,
                 bounds.y + (bounds.height - abounds.height)/2);
        }

        super.setVisible(b);
    }

    //{{DECLARE_CONTROLS
    java.awt.Label label1 = new java.awt.Label();
    java.awt.Button okButton = new java.awt.Button();
    java.awt.Label label2 = new java.awt.Label();
    //}}

    // Used for addNotify check.
    boolean fComponentsAdjusted = false;

    class SymAction implements java.awt.event.ActionListener
    {
        public void actionPerformed(java.awt.event.ActionEvent event)
        {
            Object object = event.getSource();
            if (object == okButton)
                okButton_ActionPerformed(event);
        }
    }

    void okButton_ActionPerformed(java.awt.event.ActionEvent event)
    {
        // to do: code goes here.

        okButton_ActionPerformed_Interaction1(event);
    }


    void okButton_ActionPerformed_Interaction1(java.awt.event.ActionEvent event)
    {
        try {
            this.dispose();
        } catch (Exception e) {
        }
    }


    class SymWindow extends java.awt.event.WindowAdapter
    {
        public void windowClosing(java.awt.event.WindowEvent event)
        {
            Object object = event.getSource();
            if (object == AboutDialog.this)
                AboutDialog_WindowClosing(event);
        }
    }

    void AboutDialog_WindowClosing(java.awt.event.WindowEvent event)
    {
        // to do: code goes here.

        AboutDialog_WindowClosing_Interaction1(event);
    }


    void AboutDialog_WindowClosing_Interaction1(java.awt.event.WindowEvent event)
    {
        try {
            this.dispose();
        } catch (Exception e) {
        }
    }

}
