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

package org.dcm4che.tool.findscu;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.ElementDictionary;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.tool.common.CLIUtils;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class Main {

    private static final int RELATIONAL_QUERY = 1;
    private static final int DATETIME_MATCH = 2;
    private static final int FUZZY_MATCH = 4;
    private static final int TIMEZONE_MATCH = 8;

    private static enum SOPClass {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelFIND, 0),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelFIND, 0),
        PatientStudyOnly(
                UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired, 0),
        MWL(UID.ModalityWorklistInformationModelFIND, 3),
        UPSPull(UID.UnifiedProcedureStepPullSOPClass, 3),
        UPSWatch(UID.UnifiedProcedureStepWatchSOPClass, 3),
        HANGING_PROTOCOL(UID.HangingProtocolInformationModelFIND, 3),
        COLOR_PALETTE(UID.ColorPaletteInformationModelFIND, 3);

        final String cuid;
        final int defExtNeg;

        SOPClass(String cuid, int defExtNeg) {
            this.cuid = cuid;
            this.defExtNeg = defExtNeg;
        }
    }

    private static ResourceBundle rb =
        ResourceBundle.getBundle("org.dcm4che.tool.findscu.messages");

    private static String[] IVR_LE_FIRST = {
        UID.ImplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian
    };

    private static String[] EVR_LE_FIRST = {
        UID.ExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
        UID.ImplicitVRLittleEndian
    };

    private static String[] EVR_BE_FIRST = {
        UID.ExplicitVRBigEndian,
        UID.ExplicitVRLittleEndian,
        UID.ImplicitVRLittleEndian
    };

    private static String[] IVR_LE_ONLY = {
        UID.ImplicitVRLittleEndian
    };

    private final Device device = new Device("findscu");
    private final ApplicationEntity ae = new ApplicationEntity("FINDSCU");
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority;
    private int cancelAfter;
    private SOPClass sopClass = SOPClass.StudyRoot;
    private String[] tss = IVR_LE_FIRST;
    private int extNeg;

    private File outFile;
    private int[][] outAttrs;
    private String outFormat;
    private Attributes keys = new Attributes();

    private BufferedWriter out;
    private Association as;
    private int numMatches;


    public Main() {
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
    }

    public void setScheduledExecutorService(ScheduledExecutorService service) {
        device.setScheduledExecutor(service);
    }

    public void setExecutor(Executor executor) {
        device.setExecutor(executor);
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public final void setCancelAfter(int cancelAfter) {
        this.cancelAfter = cancelAfter;
    }

    public final void setSOPClass(SOPClass sopClass) {
        this.sopClass = sopClass;
    }

    public final void setTransferSyntaxes(String[] tss) {
        this.tss = tss.clone();
    }

    private void setExtendedNegotiation(int flag, boolean enable) {
        if (enable)
            extNeg |= flag;
        else
            extNeg &= ~flag;
    }

    public void setRelationalQueries(boolean enable) {
        setExtendedNegotiation(RELATIONAL_QUERY, enable);
    }

    public void setDatetimeMatching(boolean enable) {
        setExtendedNegotiation(DATETIME_MATCH, enable);
    }

    public void setFuzzySemanticMatching(boolean enable) {
        setExtendedNegotiation(FUZZY_MATCH, enable);
    }

    public void setTimezoneQueryAdjustment(boolean enable) {
        setExtendedNegotiation(TIMEZONE_MATCH, enable);
    }

    public final void setOutputFile(File outFile) {
        this.outFile = outFile;
    }

    private void setOutputFormat(String format) {
        StringBuilder sb = new StringBuilder();
        ArrayList<int[]> attrs = new ArrayList<int[]>();
        StringTokenizer tk = new StringTokenizer(format, "{}", true);
        boolean expectTag = false;
        while (tk.hasMoreTokens()) {
            String s = tk.nextToken();
            switch (s.charAt(0)) {
            case '{':
                if (expectTag )
                    throw new IllegalArgumentException(format);
                expectTag = true;
                continue;
            case '}':
                if (!expectTag)
                    throw new IllegalArgumentException(format);
                expectTag = false;
                continue;
            }
            if (expectTag) {
                int[] tags = CLIUtils.toTags(s);
                addKey(tags, null);
                sb.append('{').append(attrs.size()).append('}');
                attrs.add(tags);
            } else {
                sb.append(s);
            }
        }
        outAttrs = attrs.toArray(new int[attrs.size()][]);
        outFormat = sb.toString();
    }

    public void addKey(int[] tags, String value) {
        Attributes item = keys;
        for (int i = 0; i < tags.length-1; i++) {
            int tag = tags[i];
            Sequence sq = (Sequence) item.getValue(tag);
            if (sq == null)
                sq = item.newSequence(tag, 1);
            if (sq.isEmpty())
                sq.add(new Attributes());
            item = sq.get(0);
        }
        int tag = tags[tags.length-1];
        VR vr = ElementDictionary.vrOf(tag,
                item.getPrivateCreator(tag));
        item.setString(tag, vr, value);
    }

    public void addKey(int tag, String value) {
        VR vr = ElementDictionary.vrOf(tag,
                keys.getPrivateCreator(tag));
        keys.setString(tag, vr, value);
    }

    private static CommandLine parseComandLine(String[] args)
                throws ParseException {
            Options opts = new Options();
            addServiceClassOptions(opts);
            addTransferSyntaxOptions(opts);
            addExtendedNegotionOptions(opts);
            addOutputOptions(opts);
            addKeyOptions(opts);
            addQueryLevelOption(opts);
            addCancelOption(opts);
            CLIUtils.addConnectOption(opts);
            CLIUtils.addBindOption(opts, "FINDSCU");
            CLIUtils.addAEOptions(opts, true, false);
            CLIUtils.addDimseRspOption(opts);
            CLIUtils.addPriorityOption(opts);
            CLIUtils.addCommonOptions(opts);
            return CLIUtils.parseComandLine(args, opts, rb, Main.class);
    }

    @SuppressWarnings("static-access")
    private static void addQueryLevelOption(Options opts) {
        opts.addOption(OptionBuilder
                .hasArgs()
                .withArgName("PATIENT|STUDY|SERIES|IMAGE")
                .withDescription(rb.getString("level"))
                .create("L"));
   }

    @SuppressWarnings("static-access")
    private static void addCancelOption(Options opts) {
        opts.addOption(OptionBuilder
                .withLongOpt("cancel")
                .hasArgs()
                .withArgName("num-matches")
                .withDescription(rb.getString("cancel"))
                .create());
    }

    @SuppressWarnings("static-access")
    private static void addKeyOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArgs()
                .withArgName("[seq/]attr=value")
                .withValueSeparator('=')
                .withDescription(rb.getString("match"))
                .create("m"));
        opts.addOption(OptionBuilder
                .hasArgs()
                .withArgName("[seq/]attr")
                .withDescription(rb.getString("return"))
                .create("r"));
    }

    @SuppressWarnings("static-access")
    private static void addOutputOptions(Options opts) {
        opts.addOption(OptionBuilder
                .withLongOpt("out")
                .hasArgs()
                .withArgName("file")
                .withDescription(rb.getString("o-file"))
                .create("o"));
        opts.addOption(OptionBuilder
                .withLongOpt("out-form")
                .hasArgs()
                .withArgName("format")
                .withDescription(rb.getString("out-form"))
                .create());
    }

    @SuppressWarnings("static-access")
    private static void addServiceClassOptions(Options opts) {
        OptionGroup group = new OptionGroup();
        group.addOption(OptionBuilder
                .withLongOpt("patient-root")
                .withDescription(rb.getString("patient-root"))
                .create("P"));
        group.addOption(OptionBuilder
                .withLongOpt("study-root")
                .withDescription(rb.getString("study-root"))
                .create("S"));
        group.addOption(OptionBuilder
                .withLongOpt("patient-study-only")
                .withDescription(rb.getString("patient-study-only"))
                .create("O"));
        group.addOption(OptionBuilder
                .withLongOpt("mwl")
                .withDescription(rb.getString("mwl"))
                .create("M"));
        group.addOption(OptionBuilder
                .withLongOpt("ups-pull")
                .withDescription(rb.getString("ups-pull"))
                .create("U"));
        group.addOption(OptionBuilder
                .withLongOpt("ups-watch")
                .withDescription(rb.getString("ups-watch"))
                .create("W"));
        group.addOption(OptionBuilder
                .withLongOpt("hanging-protocol")
                .withDescription(rb.getString("hanging-protocol"))
                .create("H"));
        group.addOption(OptionBuilder
                .withLongOpt("color-palette")
                .withDescription(rb.getString("color-palette"))
                .create("C"));
        opts.addOptionGroup(group);
    }

    private static void addExtendedNegotionOptions(Options opts) {
        opts.addOption(null, "relational", false, rb.getString("relational"));
        opts.addOption(null, "datetime", false, rb.getString("datetime"));
        opts.addOption(null, "fuzzy", false, rb.getString("fuzzy"));
        opts.addOption(null, "timezone", false, rb.getString("timezone"));
    }

    @SuppressWarnings("static-access")
    private static void addTransferSyntaxOptions(Options opts) {
        OptionGroup group = new OptionGroup();
        group.addOption(OptionBuilder
                .withLongOpt("explicit-vr")
                .withDescription(rb.getString("explicit-vr"))
                .create());
        group.addOption(OptionBuilder
                .withLongOpt("big-endian")
                .withDescription(rb.getString("big-endian"))
                .create());
        group.addOption(OptionBuilder
                .withLongOpt("implicit-vr")
                .withDescription(rb.getString("implicit-vr"))
                .create());
       opts.addOptionGroup(group);
    }

    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            Main main = new Main();
            CLIUtils.configureConnect(main.remote, main.rq, cl);
            CLIUtils.configureBind(main.conn, main.ae, cl);
            CLIUtils.configure(main.conn, main.ae, cl);
            configureOutput(main, cl);
            configureKeys(main, cl);
            configureCancel(main, cl);
            main.setSOPClass(sopClassOf(cl));
            main.setTransferSyntaxes(tssOf(cl));
            main.setPriority(CLIUtils.priorityOf(cl));
            configureExtendedNegotiation(main, cl);
            ExecutorService executorService =
                    Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor();
            main.setExecutor(executorService);
            main.setScheduledExecutorService(scheduledExecutorService);
            try {
                main.open();
                main.query();
            } finally {
                main.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
       } catch (ParseException e) {
            System.err.println("findscu: " + e.getMessage());
            System.err.println(rb.getString("try"));
            System.exit(2);
        } catch (Exception e) {
            System.err.println("findscu: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void configureExtendedNegotiation(Main main, CommandLine cl) {
        main.setRelationalQueries(cl.hasOption("relational"));
        main.setDatetimeMatching(cl.hasOption("datetime"));
        main.setFuzzySemanticMatching(cl.hasOption("fuzzy"));
        main.setTimezoneQueryAdjustment(cl.hasOption("timezone"));
    }

    private static void configureOutput(Main main, CommandLine cl) {
        if (cl.hasOption("o"))
            main.setOutputFile(new File(cl.getOptionValue("o")));
        if (cl.hasOption("out-form"))
            main.setOutputFormat(cl.getOptionValue("out-form"));
    }

    private static void configureCancel(Main main, CommandLine cl) {
        if (cl.hasOption("cancel"))
            main.setCancelAfter(Integer.parseInt(cl.getOptionValue("C")));
    }

    private static void configureKeys(Main main, CommandLine cl) {
        if (cl.hasOption("r")) {
            String[] keys = cl.getOptionValues("r");
            for (int i = 0; i < keys.length; i++)
                main.addKey(CLIUtils.toTags(keys[i]), null);
        }
        if (cl.hasOption("m")) {
            String[] keys = cl.getOptionValues("m");
            for (int i = 1; i < keys.length; i++, i++)
                main.addKey(CLIUtils.toTags(keys[i - 1]), keys[i]);
        }
        if (cl.hasOption("L"))
            main.addKey(Tag.QueryRetrieveLevel, cl.getOptionValue("L"));
    }

    private static SOPClass sopClassOf(CommandLine cl) throws ParseException {
        if (cl.hasOption("P")) {
            requiresQueryLevel("P", cl);
            return SOPClass.PatientRoot;
        }
        if (cl.hasOption("S")) {
            requiresQueryLevel("S", cl);
            return SOPClass.StudyRoot;
        }
        if (cl.hasOption("O")) {
            requiresQueryLevel("O", cl);
            return SOPClass.PatientStudyOnly;
        }
        if (cl.hasOption("M")) {
            noQueryLevel("M", cl);
            return SOPClass.MWL;
        }
        if (cl.hasOption("U")) {
            noQueryLevel("U", cl);
            return SOPClass.UPSPull;
        }
        if (cl.hasOption("W")) {
            noQueryLevel("W", cl);
            return SOPClass.UPSWatch;
        }
        if (cl.hasOption("H")) {
            noQueryLevel("H", cl);
            return SOPClass.HANGING_PROTOCOL;
        }
        if (cl.hasOption("C")) {
            noQueryLevel("C", cl);
            return SOPClass.COLOR_PALETTE;
        }
        throw new ParseException(rb.getString("missing"));
    }

    private static void requiresQueryLevel(String opt, CommandLine cl)
            throws ParseException {
        if (!cl.hasOption("L"))
            throw new ParseException(
                    MessageFormat.format(rb.getString("missing-level"), opt));
    }

    private static void noQueryLevel(String opt, CommandLine cl)
            throws ParseException {
        if (cl.hasOption("L"))
            throw new ParseException(
                    MessageFormat.format(rb.getString("invalid-level"), opt));
    }

    private static String[] tssOf(CommandLine cl) {
        if (cl.hasOption("explicit-vr"))
            return EVR_LE_FIRST;
        if (cl.hasOption("big-endian"))
            return EVR_BE_FIRST;
        if (cl.hasOption("implicit-vr"))
            return IVR_LE_ONLY;
        return IVR_LE_FIRST;
    }

    public void open() throws IOException, InterruptedException {
        rq.addPresentationContext(
                new PresentationContext(1, sopClass.cuid, tss));
        if (extNeg > sopClass.defExtNeg)
            rq.addExtendedNegotiation(
                    new ExtendedNegotiation(sopClass.cuid,
                            toInfo(extNeg | sopClass.defExtNeg)));
        as = ae.connect(conn, remote.getHostname(), remote.getPort(), rq);
    }

    private static byte[] toInfo(int extNeg) {
        if (extNeg == 1)
            return new byte[] { 1 };
        byte[] info = new byte[(extNeg & 8) == 0 ? 3 : 4];
        for (int i = 0; i < info.length; i++, extNeg >>>= 1)
            info[i] = (byte) (extNeg & 1);
        return info;
    }

    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer())
            as.release();
        if (out != null)
            SafeClose.close(out);
    }

    private void query() throws IOException, InterruptedException {
        if (outFormat != null) {
            out = new BufferedWriter(
                    new OutputStreamWriter(
                            outFile != null 
                            ? new FileOutputStream(outFile)
                            : new FileOutputStream(FileDescriptor.out)));
        }
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd,
                    Attributes data) {
                super.onDimseRSP(as, cmd, data);
                Main.this.onCFindRSP(cmd, data, this);
            }
        };

        as.cfind(sopClass.cuid, priority, keys, null, rspHandler);
        as.waitForOutstandingRSP();
    }

    private void onCFindRSP(Attributes cmd, Attributes data, 
            DimseRSPHandler rspHandler) {
        int status = cmd.getInt(Tag.Status, -1);
        if (Commands.isPending(status )) {
            if (outFormat != null)
                try {
                    out.write(MessageFormat.format(outFormat, outValues(data)));
                    out.newLine();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            ++numMatches;
            if (cancelAfter != 0 && numMatches >= cancelAfter)
                try {
                    rspHandler.cancel(as);
                    cancelAfter = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    private Object[] outValues(Attributes data) {
        Object[] values = new Object[outAttrs.length];
        for (int i = 0; i < values.length; i++)
            values[i] = getString(data, outAttrs[i]);
        return values ;
    }

    private Object getString(Attributes data, int[] tags) {
        try {
            for (int i = 0; i < tags.length-1; i++) 
                 data = ((Sequence) data.getValue(tags[i])).get(0);
            String[] ss = data.getStrings(tags[tags.length-1]);
            if (ss != null && ss.length != 0)
                return StringUtils.join(ss, '\\');
        } catch (Exception e) {
        }
        return "";
    }

}
