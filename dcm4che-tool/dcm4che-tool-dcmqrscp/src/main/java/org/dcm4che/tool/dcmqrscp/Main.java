/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che.tool.dcmqrscp;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dcm4che.data.UID;
import org.dcm4che.media.DicomDirReader;
import org.dcm4che.media.DicomDirWriter;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4che.net.service.CEchoService;
import org.dcm4che.tool.common.CLIUtils;
import org.dcm4che.tool.common.FilesetInfo;
import org.dcm4che.util.StringUtils;
import org.dcm4che.util.UIDUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class Main {

    private static ResourceBundle rb =
        ResourceBundle.getBundle("org.dcm4che.tool.dcmqrscp.messages");

    private final Device device = new Device("dcmqrscp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();

    private final CStoreService storageSCP = new CStoreService(this);

    private File storageDir;
    private File dicomDir;
    private final FilesetInfo fsInfo = new FilesetInfo();
    private DicomDirReader ddReader;
    private DicomDirReader ddWriter;

    public Main() {
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new CEchoService());
        serviceRegistry.addDicomService(storageSCP);
        serviceRegistry.addDicomService(
                new CFindService(this,
                        UID.PatientRootQueryRetrieveInformationModelFIND,
                        "PATIENT", "STUDY", "SERIES", "IMAGE"));
        serviceRegistry.addDicomService(
                new CFindService(this,
                        UID.StudyRootQueryRetrieveInformationModelFIND,
                        "STUDY", "SERIES", "IMAGE"));
        serviceRegistry.addDicomService(
                new CFindService(this,
                        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
                        "PATIENT", "STUDY"));
       ae.setDimseRQHandler(serviceRegistry);
    }

    final Device getDevice() {
        return device;
    }

    public final void setStorageDirectory(File storageDir) {
        if (storageDir.mkdirs())
            System.out.println("M-WRITE " + storageDir);
        this.storageDir = storageDir;
    }

    public final File getStorageDirectory() {
        return storageDir;
    }

    public final void setDicomDirectory(File dicomDir) {
        setStorageDirectory(dicomDir.getParentFile());
        this.dicomDir = dicomDir;
    }

    public final File getDicomDirectory() {
        return dicomDir;
    }

    public void setScheduledExecuter(ScheduledExecutorService scheduledExecutor) {
        device.setScheduledExecutor(scheduledExecutor);
    }

    public void setExecutor(Executor executor) {
        device.setExecutor(executor);
    }

    private static CommandLine parseComandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        CLIUtils.addFilesetInfoOptions(opts);
        CLIUtils.addBindServerOption(opts);
        CLIUtils.addAEOptions(opts, false, true);
        CLIUtils.addCommonOptions(opts);
        addStorageDirectoryOptions(opts);
        addTransferCapabilityOptions(opts);
        return CLIUtils.parseComandLine(args, opts, rb, Main.class);
    }

    @SuppressWarnings("static-access")
    private static void addStorageDirectoryOptions(Options opts) {
        OptionGroup group = new OptionGroup();
        group.addOption(OptionBuilder
                .hasArg()
                .withArgName("file")
                .withDescription(rb.getString("dicomdir"))
                .withLongOpt("dicomdir")
                .create("D"));
        group.addOption(OptionBuilder
                .hasArg()
                .withArgName("directory")
                .withDescription(rb.getString("storedir"))
                .withLongOpt("storedir")
                .create("d"));
        opts.addOptionGroup(group);
    }

    @SuppressWarnings("static-access")
    private static void addTransferCapabilityOptions(Options opts) {
        opts.addOption(null, "all-storage", false, rb.getString("all-storage"));
        opts.addOption(null, "no-storage", false, rb.getString("no-storage"));
        opts.addOption(null, "no-query", false, rb.getString("no-query"));
        opts.addOption(null, "no-retrieve", false, rb.getString("no-retrieve"));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("file|url")
                .withDescription(rb.getString("storage-sop-classes"))
                .withLongOpt("storage-sop-classes")
                .create());
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("file|url")
                .withDescription(rb.getString("query-sop-classes"))
                .withLongOpt("query-sop-classes")
                .create());
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("file|url")
                .withDescription(rb.getString("retrieve-sop-classes"))
                .withLongOpt("retrieve-sop-classes")
                .create());
    }

    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            Main main = new Main();
            CLIUtils.configure(main.fsInfo, cl);
            CLIUtils.configureBindServer(main.conn, main.ae, cl);
            CLIUtils.configure(main.conn, main.ae, cl);
            configureStorageDirectory(main, cl);
            configureTransferCapability(main, cl);
            ExecutorService executorService = Executors.newCachedThreadPool();
            ScheduledExecutorService scheduledExecutorService = 
                    Executors.newSingleThreadScheduledExecutor();
            main.setScheduledExecuter(scheduledExecutorService);
            main.setExecutor(executorService);
            main.start();
        } catch (ParseException e) {
            System.err.println("dcmqrscp: " + e.getMessage());
            System.err.println(rb.getString("try"));
            System.exit(2);
        } catch (Exception e) {
            System.err.println("dcmqrscp: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void configureStorageDirectory(Main main, CommandLine cl)
            throws ParseException {
        if (cl.hasOption("D"))
            main.setDicomDirectory(new File(cl.getOptionValue("D")));
        else if (cl.hasOption("d"))
            main.setStorageDirectory(new File(cl.getOptionValue("d")));
    }

    private static void configureTransferCapability(Main main, CommandLine cl)
            throws IOException {
        ApplicationEntity ae = main.ae;
        File dir = main.getStorageDirectory();
        boolean storage = !cl.hasOption("no-storage")
                && (dir == null || dir.canWrite());
        boolean dicomdir = main.getDicomDirectory() != null;
        if (storage && cl.hasOption("all-storage")) {
            ae.addTransferCapability(
                    new TransferCapability(null, 
                            "*",
                            TransferCapability.Role.SCP,
                            "*"));
        } else {
            ae.addTransferCapability(
                    new TransferCapability(null, 
                            UID.VerificationSOPClass,
                            TransferCapability.Role.SCP,
                            UID.ImplicitVRLittleEndian));
            Properties storageSOPClasses = CLIUtils.loadProperties(
                    cl.getOptionValue("storage-sop-classes",
                            "resource:storage-sop-classes.properties"),
                    null);
            if (storage)
                addTransferCapabilities(ae, storageSOPClasses,
                        TransferCapability.Role.SCP);
            if (dir != null && !cl.hasOption("no-retrieve")) {
                addTransferCapabilities(ae, storageSOPClasses,
                        TransferCapability.Role.SCU);
                Properties p = CLIUtils.loadProperties(
                        cl.getOptionValue("retrieve-sop-classes",
                                "resource:retrieve-sop-classes.properties"),
                        null);
                addTransferCapabilities(ae, p, TransferCapability.Role.SCP);
            }
           if (dicomdir && !cl.hasOption("no-query")) {
                Properties p = CLIUtils.loadProperties(
                        cl.getOptionValue("query-sop-classes",
                                "resource:query-sop-classes.properties"),
                        null);
                addTransferCapabilities(ae, p, TransferCapability.Role.SCP);
            }
        }
        if (dicomdir) {
            if (storage)
                main.openDicomDir();
            else
                main.openDicomDirForReadOnly();
        }
     }

    private static void addTransferCapabilities(ApplicationEntity ae,
            Properties p, TransferCapability.Role role) {
        for (String cuid : p.stringPropertyNames()) {
            String ts = p.getProperty(cuid);
            ae.addTransferCapability(
                    ts.equals("*")
                        ? new TransferCapability(null, cuid, role, "*")
                        : new TransferCapability(null, cuid, role,
                                toUIDs(StringUtils.split(ts, ','))));
        }
    }

    private static String[] toUIDs(String[] names) {
        String[] uids = new String[names.length];
        for (int i = 0; i < uids.length; i++)
            uids[i] = UID.forName(names[i].trim());
        return uids ;
    }

    private void start() throws IOException {
        conn.bind();
    }

    DicomDirReader getDicomDirReader() {
         return ddReader;
    }

    DicomDirReader getDicomDirWriter() {
         return ddWriter;
    }

    private void openDicomDir() throws IOException {
        if (!dicomDir.exists())
            DicomDirWriter.createEmptyDirectory(dicomDir,
                    UIDUtils.createUIDIfNull(fsInfo.getFilesetUID()),
                    fsInfo.getFilesetID(),
                    fsInfo.getDescriptorFile(), 
                    fsInfo.getDescriptorFileCharset());
        ddReader = ddWriter = DicomDirWriter.open(dicomDir);
    }

    private void openDicomDirForReadOnly() throws IOException {
        ddReader = ddWriter = new DicomDirReader(dicomDir);
    }

}
