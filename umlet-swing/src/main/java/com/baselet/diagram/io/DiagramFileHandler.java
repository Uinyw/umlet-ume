package com.baselet.diagram.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.baselet.util.logging.Logger;
import com.baselet.util.logging.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.baselet.control.config.Config;
import com.baselet.control.constants.Constants;
import com.baselet.control.enums.Program;
import com.baselet.control.util.Path;
import com.baselet.control.util.RecentlyUsedFilesList;
import com.baselet.diagram.CurrentDiagram;
import com.baselet.diagram.DiagramHandler;
import com.baselet.diagram.Notifier;
import com.baselet.element.NewGridElement;
import com.baselet.element.interfaces.GridElement;
import com.baselet.element.old.custom.CustomElement;
import com.baselet.gui.CurrentGui;

public class DiagramFileHandler {

	private static final Logger log = LoggerFactory.getLogger(DiagramFileHandler.class);

	private String fileName;
	private final DiagramHandler handler;
	private File file;
	private File lastExportFile;
	private final HashMap<String, FileFilter> filters = new HashMap<String, FileFilter>();
	private final HashMap<FileFilter, String> fileextensions = new HashMap<FileFilter, String>();

	private final OwnFileFilter filterxml = new OwnFileFilter(Program.getInstance().getExtension(), Program.getInstance().getProgramName() + " diagram format");
	private final OwnFileFilter filterbmp = new OwnFileFilter("bmp", "BMP");
	private final OwnFileFilter filtereps = new OwnFileFilter("eps", "EPS");
	private final OwnFileFilter filtergif = new OwnFileFilter("gif", "GIF");
	private final OwnFileFilter filterjpg = new OwnFileFilter("jpg", "JPG");
	private final OwnFileFilter filterpdf = new OwnFileFilter("pdf", "PDF");
	private final OwnFileFilter filterpng = new OwnFileFilter("png", "PNG");
	private final OwnFileFilter filtersvg = new OwnFileFilter("svg", "SVG");

	private final OwnFileFilter[] saveFileFilter = new OwnFileFilter[] { filterxml };
	private final OwnFileFilter[] exportFileFilter = new OwnFileFilter[] { filterbmp, filtereps, filtergif, filterjpg, filterpdf, filterpng, filtersvg };
	private final List<OwnFileFilter> allFileFilters = new ArrayList<OwnFileFilter>();

	protected DiagramFileHandler(DiagramHandler diagramHandler, File file) {
		handler = diagramHandler;
		if (file != null) {
			fileName = file.getName();
		}
		else {
			fileName = "new." + Program.getInstance().getExtension();
		}
		this.file = file;
		lastExportFile = file;

		allFileFilters.addAll(Arrays.asList(saveFileFilter));
		allFileFilters.addAll(Arrays.asList(exportFileFilter));
		for (OwnFileFilter filter : allFileFilters) {
			filters.put(filter.getFormat(), filter);
			fileextensions.put(filter, filter.getFormat());
		}
	}

	public static DiagramFileHandler createInstance(DiagramHandler diagramHandler, File file) {
		return new DiagramFileHandler(diagramHandler, file);
	}

	private JFileChooser createSaveFileChooser(boolean exportCall, String filePath) {
		File initialDirectory;
		if (filePath != null && !filePath.isEmpty()) {
			initialDirectory = new File(directory(filePath));
		}
		else {
			initialDirectory = calcInitialDir(exportCall);
		}

		JFileChooser fileChooser = new JFileChooser(initialDirectory);
		fileChooser.setAcceptAllFileFilterUsed(false); // We don't want "all files" as a choice
		// The input field should show the diagram name as preset
		File selectedFile;
		if (filePath != null && !filePath.isEmpty()) {
			selectedFile = new File(filename(filePath));
		}
		else {
			selectedFile = new File(CurrentDiagram.getInstance().getDiagramHandler().getName());
		}
		fileChooser.setSelectedFile(selectedFile);
		return fileChooser;
	}

	private File calcInitialDir(boolean exportCall) {
		if (exportCall && lastExportFile != null) { // if this is an export-diagram call the diagram was exported once before (for consecutive export calls - see Issue 82)
			return lastExportFile;
		}
		else if (file != null) { // otherwise if diagram has a fixed uxf path, use this
			return file;
		}
		else { // otherwise use the last used save path
			return new File(Config.getInstance().getSaveFileHome());
		}
	}

	public String getFileName() {
		return fileName;
	}

	public String getFullPathName() {
		if (file != null) {
			return file.getAbsolutePath();
		}
		return "";
	}

	private void setFileName(String fileName) {
		this.fileName = fileName;
		CurrentGui.getInstance().getGui().updateDiagramName(handler, handler.getName());
	}

	private void createXMLOutputDoc(Document doc, Collection<GridElement> elements, Element current) {
		for (GridElement e : elements) {
			appendRecursively(doc, current, e);
		}
	}

	private void appendRecursively(Document doc, Element parentXmlElement, GridElement e) {
		parentXmlElement.appendChild(createXmlElementForGridElement(doc, e));
	}

	private Element createXmlElementForGridElement(Document doc, GridElement e) {
		// insert normal entity element
		java.lang.Class<? extends GridElement> c = e.getClass();
		String sElType = c.getName();
		String sElPanelAttributes = e.getPanelAttributes();
		String sElAdditionalAttributes = e.getAdditionalAttributes();

		Element el = doc.createElement("element");

		if (e instanceof NewGridElement) {
			Element elType = doc.createElement("id");
			elType.appendChild(doc.createTextNode(((NewGridElement) e).getId().toString()));
			el.appendChild(elType);
		}
		else { // OldGridElement
			Element elType = doc.createElement("type");
			elType.appendChild(doc.createTextNode(sElType));
			el.appendChild(elType);
		}

		Element elCoor = doc.createElement("coordinates");
		el.appendChild(elCoor);

		Element elX = doc.createElement("x");
		elX.appendChild(doc.createTextNode("" + e.getRectangle().x));
		elCoor.appendChild(elX);

		Element elY = doc.createElement("y");
		elY.appendChild(doc.createTextNode("" + e.getRectangle().y));
		elCoor.appendChild(elY);

		Element elW = doc.createElement("w");
		elW.appendChild(doc.createTextNode("" + e.getRectangle().width));
		elCoor.appendChild(elW);

		Element elH = doc.createElement("h");
		elH.appendChild(doc.createTextNode("" + e.getRectangle().height));
		elCoor.appendChild(elH);

		Element elPA = doc.createElement("panel_attributes");
		elPA.appendChild(doc.createTextNode(sElPanelAttributes));
		el.appendChild(elPA);

		Element elAA = doc.createElement("additional_attributes");
		elAA.appendChild(doc.createTextNode(sElAdditionalAttributes));
		el.appendChild(elAA);

		if (e instanceof CustomElement) {
			Element elCO = doc.createElement("custom_code");
			elCO.appendChild(doc.createTextNode(((CustomElement) e).getCode()));
			el.appendChild(elCO);
		}
		return el;
	}

	protected String createStringToBeSaved() {
		DocumentBuilder db = null;
		String returnString = null;

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();

			Element root = doc.createElement("diagram");
			root.setAttribute("program", Program.getInstance().getProgramName().toLowerCase());
			root.setAttribute("version", String.valueOf(Program.getInstance().getVersion()));
			doc.appendChild(root);

			// save helptext
			String helptext = handler.getHelpText();
			if (!helptext.equals(Constants.getDefaultHelptext())) {
				Element help = doc.createElement("help_text");
				help.appendChild(doc.createTextNode(helptext));
				root.appendChild(help);
			}

			// save zoom
			Element zoom = doc.createElement("zoom_level");
			zoom.appendChild(doc.createTextNode(String.valueOf(handler.getGridSize())));
			root.appendChild(zoom);

			createXMLOutputDoc(doc, handler.getDrawPanel().getGridElements(), root);

			// output the stuff...
			DOMSource source = new DOMSource(doc);
			StringWriter stringWriter = new StringWriter();
			StreamResult result = new StreamResult(stringWriter);

			TransformerFactory transFactory = TransformerFactory.newInstance();
			Transformer transformer = transFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			transformer.transform(source, result);

			returnString = stringWriter.toString();
		} catch (Exception e) {
			log.error("Error saving XML.", e);
		}

		return returnString;

	}

	public void doOpen() {
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			if (Config.getInstance().isSecureXmlProcessing()) {
				// use secure xml processing (see https://www.owasp.org/index.php/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet#JAXP_DocumentBuilderFactory.2C_SAXParserFactory_and_DOM4J)
				spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			}
			SAXParser parser = spf.newSAXParser();
			FileInputStream input = new FileInputStream(file);
			InputHandler xmlhandler = new InputHandler(handler);
			parser.parse(input, xmlhandler);
			input.close();
		} catch (Exception e) {
			log.error("Cannot open the file: " + file.getAbsolutePath(), e);
		}
	}

	public String doSaveAs(String filePath, String extension) throws IOException {
		boolean ownXmlFormat = extension.equals(Program.getInstance().getExtension());
		JFileChooser fileChooser = createSaveFileChooser(!ownXmlFormat, filePath);

		String chosenFileName = chooseFileName(ownXmlFormat, filters.get(extension), fileChooser);
		String chosenExtension = fileextensions.get(fileChooser.getFileFilter());
		if (chosenFileName == null) {
			return null; // If the filechooser has been closed without saving
		}
		if (!chosenFileName.endsWith("." + chosenExtension)) {
			chosenFileName += "." + chosenExtension;
		}

		File fileToSave = new File(chosenFileName);
		Config.getInstance().setSaveFileHome(fileToSave.getParent());
		if (chosenExtension.equals(Program.getInstance().getExtension())) {
			file = fileToSave;
			setFileName(file.getName());
			save();
		}
		else {
			lastExportFile = fileToSave;
			doExportAs(extension, fileToSave);
		}

		return fileToSave.getAbsolutePath();
	}

	public String doSaveAs(String fileextension) throws IOException {
		return doSaveAs(null, fileextension);
	}

	public File doSaveTempDiagram(String filename, String fileextension) throws IOException {
		File tempFile = new File(Path.temp() + filename + "." + fileextension);
		tempFile.deleteOnExit();

		if (fileextension.equals(Program.getInstance().getExtension())) {
			save(tempFile, true);
		}
		else {
			doExportAs(fileextension, tempFile);
		}

		return tempFile;
	}

	public void doSave() throws IOException {
		if (file == null || !file.exists()) {
			doSaveAs(Program.getInstance().getExtension());
		}
		else {
			save();
		}
	}

	public void doExportAs(String extension, File file) throws IOException {
		// CustomElementSecurityManager.addThreadPrivileges(Thread.currentThread(), fileName);
		try {
			OutputHandler.createAndOutputToFile(extension, file, handler);
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
		// CustomElementSecurityManager.remThreadPrivileges(Thread.currentThread());
	}

	public void doExportAsXMI() throws IOException {
		final String chosenExtension = "xml";
		JFileChooser fileChooser = createSaveFileChooser(true, null);
		String chosenFileName = chooseFileName(true, new OwnFileFilter(chosenExtension, "XML"), fileChooser);
		if (chosenFileName == null) {
			return;
		}
		if (!chosenFileName.endsWith("." + chosenExtension)) {
			if (chosenFileName.endsWith(".null")) {
				chosenFileName = chosenFileName.substring(0, chosenFileName.length() - 5);
			}
			chosenFileName += "." + chosenExtension;
		}

		File fileToSave = new File(chosenFileName);

        String tmp;
        try {
            tmp = buildXMI();
        } catch (ParserConfigurationException | TransformerException e) {
            throw new RuntimeException(e);
        }
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileToSave), "UTF-8"));
		out.print(tmp);
		out.close();
		Notifier.getInstance().showInfo("Huhu");
	}

	private String buildXMI() throws ParserConfigurationException, TransformerException {
		final String UML_NS = "http://www.omg.org/spec/UML/20090901";
		final String XMI_NS = "http://www.omg.org/XMI";
		final String UML_PRIMITIVE_TYPES = "http://www.omg.org/spec/UML/20090901/PrimitiveTypes.xml";

		// Initialize XML document
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document xmiDoc = db.newDocument();

		// Root <xmi:XMI>
		Element xmiRoot = xmiDoc.createElementNS(XMI_NS, "xmi:XMI");
		xmiRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xmi", XMI_NS);
		xmiRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:uml", UML_NS);
		xmiRoot.setAttribute("xmi:version", "2.1");
		xmiDoc.appendChild(xmiRoot);

		// UML Model
		Element model = xmiDoc.createElementNS(UML_NS, "uml:Model");
		model.setAttribute("xmi:type", "uml:Model");
		model.setAttribute("xmi:id", "model1");
		model.setAttribute("name", "UMLetExport");
		xmiRoot.appendChild(model);

		// ---------- PASS 1: Collect all classes ----------
		Map<String, String> classNameToId = new HashMap<String, String>();
		List<NewGridElement> classElements = new ArrayList<NewGridElement>();
		int classCounter = 1;

		for (GridElement element : handler.getDrawPanel().getGridElements()) {
			if (element instanceof NewGridElement) {
				NewGridElement ge = (NewGridElement) element;
				if ("UMLClass".equals(ge.getId().toString())) {
					String raw = ge.getPanelAttributes().replace("&lt;", "<").replace("&gt;", ">");
					String[] lines = raw.split("\n");

					String className = "";
					for (String line : lines) {
						if (!line.trim().isEmpty() && !line.trim().startsWith("<<") && !line.contains(":")) {
							className = line.trim();
							break;
						}
					}

					if (!className.isEmpty()) {
						String classId = "class" + classCounter++;
						classNameToId.put(className, classId);
						classElements.add(ge);
					}
				}
			}
		}

		// ---------- PASS 2: Build UML classes ----------
		int featureCounter = 1; // shared counter for attributes + operations
		for (NewGridElement ge : classElements) {
			String raw = ge.getPanelAttributes().replace("&lt;", "<").replace("&gt;", ">");
			String[] lines = raw.split("\n");

			String stereotype = "";
			String className = "";
			List<String> attributes = new ArrayList<String>();
			List<String> operations = new ArrayList<String>();

			boolean inAttributes = false, inOperations = false;

			for (String line : lines) {
				line = line.trim();
				if (line.isEmpty()) continue;

				if (line.startsWith("<<") && line.endsWith(">>")) {
					stereotype = line.substring(2, line.length() - 2).trim();
				} else if (className.isEmpty() && !line.contains(":") && !line.equals("-")) {
					className = line;
				} else if (line.equals("-")) {
					if (!inAttributes) { inAttributes = true; }
					else if (!inOperations) { inAttributes = false; inOperations = true; }
				} else if (inAttributes) {
					attributes.add(line);
				} else if (inOperations) {
					operations.add(line);
				}
			}

			String classId = classNameToId.get(className);
			Element classElement = xmiDoc.createElementNS(UML_NS, "packagedElement");
			classElement.setAttribute("xmi:type", "uml:Class");
			classElement.setAttribute("xmi:id", classId);
			classElement.setAttribute("name", className);
			if (!stereotype.isEmpty()) {
				classElement.setAttribute("stereotype", stereotype);
			}

			// ----- Attributes -----
			for (String attr : attributes) {
				String[] parts = attr.split(":");
				if (parts.length == 2) {
					String attrName = parts[0].trim();
					String attrType = parts[1].trim();

					boolean isArray = attrType.endsWith("[]");
					if (isArray) {
						attrType = attrType.substring(0, attrType.length() - 2);
					}

					Element attrElem = xmiDoc.createElementNS(UML_NS, "ownedAttribute");
					attrElem.setAttribute("name", attrName);
					attrElem.setAttribute("xmi:id", classId + "_attr" + featureCounter++);

					// Type reference
					Element typeElem = xmiDoc.createElementNS(UML_NS, "type");
					if (classNameToId.containsKey(attrType)) {
						typeElem.setAttribute("xmi:type", "uml:Class");
						typeElem.setAttribute("href", "#" + classNameToId.get(attrType));
					} else {
						typeElem.setAttribute("xmi:type", "uml:PrimitiveType");
						typeElem.setAttribute("href", UML_PRIMITIVE_TYPES + "#" + attrType);
					}
					attrElem.appendChild(typeElem);

					// Multiplicity for arrays
					if (isArray) {
						Element lower = xmiDoc.createElementNS(UML_NS, "lowerValue");
						lower.setAttribute("xmi:type", "uml:LiteralInteger");
						lower.setAttribute("xmi:id", attrElem.getAttribute("xmi:id") + "_lower");
						lower.setAttribute("value", "0");

						Element upper = xmiDoc.createElementNS(UML_NS, "upperValue");
						upper.setAttribute("xmi:type", "uml:LiteralUnlimitedNatural");
						upper.setAttribute("xmi:id", attrElem.getAttribute("xmi:id") + "_upper");
						upper.setAttribute("value", "*");

						attrElem.appendChild(lower);
						attrElem.appendChild(upper);
					}

					classElement.appendChild(attrElem);
				}
			}

			// ----- Operations -----
			for (String op : operations) {
				// Example: create(input: Input): Output
				String opName = op;
				String paramPart = "";
				String returnType = null;

				// Extract return type (last colon after params)
				if (op.contains(":")) {
					int lastColon = op.lastIndexOf(":");
					returnType = op.substring(lastColon + 1).trim();
					opName = op.substring(0, lastColon).trim();
				}

				// Extract parameters
				List<String[]> params = new ArrayList<String[]>();
				int start = opName.indexOf("(");
				int end = opName.indexOf(")");
				if (start != -1 && end != -1 && end > start) {
					paramPart = opName.substring(start + 1, end).trim();
					opName = opName.substring(0, start).trim();

					if (!paramPart.isEmpty()) {
						for (String p : paramPart.split(",")) {
							String[] parts = p.trim().split(":");
							if (parts.length == 2) {
								params.add(new String[]{parts[0].trim(), parts[1].trim()});
							}
						}
					}
				}

				Element opElem = xmiDoc.createElementNS(UML_NS, "ownedOperation");
				opElem.setAttribute("name", opName);
				opElem.setAttribute("xmi:id", classId + "_op" + featureCounter++);

				int paramIndex = 1;
				for (String[] param : params) {
					String paramName = param[0];
					String paramType = param[1];
					boolean isArray = paramType.endsWith("[]");
					if (isArray) {
						paramType = paramType.substring(0, paramType.length() - 2);
					}

					Element paramElem = xmiDoc.createElementNS(UML_NS, "ownedParameter");
					paramElem.setAttribute("xmi:id", classId + "_op" + (featureCounter - 1) + "_p" + paramIndex++);
					paramElem.setAttribute("name", paramName);
					paramElem.setAttribute("direction", "in");

					Element typeElem = xmiDoc.createElementNS(UML_NS, "type");
					if (classNameToId.containsKey(paramType)) {
						typeElem.setAttribute("xmi:type", "uml:Class");
						typeElem.setAttribute("href", "#" + classNameToId.get(paramType));
					} else {
						typeElem.setAttribute("xmi:type", "uml:PrimitiveType");
						typeElem.setAttribute("href", UML_PRIMITIVE_TYPES + "#" + paramType);
					}
					paramElem.appendChild(typeElem);

					if (isArray) {
						Element lower = xmiDoc.createElementNS(UML_NS, "lowerValue");
						lower.setAttribute("xmi:type", "uml:LiteralInteger");
						lower.setAttribute("xmi:id", paramElem.getAttribute("xmi:id") + "_lower");
						lower.setAttribute("value", "0");

						Element upper = xmiDoc.createElementNS(UML_NS, "upperValue");
						upper.setAttribute("xmi:type", "uml:LiteralUnlimitedNatural");
						upper.setAttribute("xmi:id", paramElem.getAttribute("xmi:id") + "_upper");
						upper.setAttribute("value", "*");

						paramElem.appendChild(lower);
						paramElem.appendChild(upper);
					}

					opElem.appendChild(paramElem);
				}

				// Return type
				if (returnType != null && !returnType.isEmpty()) {
					boolean isArray = returnType.endsWith("[]");
					if (isArray) {
						returnType = returnType.substring(0, returnType.length() - 2);
					}

					Element retElem = xmiDoc.createElementNS(UML_NS, "ownedParameter");
					retElem.setAttribute("xmi:id", classId + "_op" + (featureCounter - 1) + "_ret");
					retElem.setAttribute("name", "return");
					retElem.setAttribute("direction", "return");

					Element typeElem = xmiDoc.createElementNS(UML_NS, "type");
					if (classNameToId.containsKey(returnType)) {
						typeElem.setAttribute("xmi:type", "uml:Class");
						typeElem.setAttribute("href", "#" + classNameToId.get(returnType));
					} else {
						typeElem.setAttribute("xmi:type", "uml:PrimitiveType");
						typeElem.setAttribute("href", UML_PRIMITIVE_TYPES + "#" + returnType);
					}
					retElem.appendChild(typeElem);

					if (isArray) {
						Element lower = xmiDoc.createElementNS(UML_NS, "lowerValue");
						lower.setAttribute("xmi:type", "uml:LiteralInteger");
						lower.setAttribute("xmi:id", retElem.getAttribute("xmi:id") + "_lower");
						lower.setAttribute("value", "0");

						Element upper = xmiDoc.createElementNS(UML_NS, "uml:LiteralUnlimitedNatural");
						upper.setAttribute("xmi:id", retElem.getAttribute("xmi:id") + "_upper");
						upper.setAttribute("value", "*");

						retElem.appendChild(lower);
						retElem.appendChild(upper);
					}

					opElem.appendChild(retElem);
				}

				classElement.appendChild(opElem);
			}

			model.appendChild(classElement);
		}

		// ---------- Output ----------
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

		StringWriter stringWriter = new StringWriter();
		transformer.transform(new DOMSource(xmiDoc), new StreamResult(stringWriter));
		return stringWriter.toString();
	}



	private void save() throws UnsupportedEncodingException, FileNotFoundException {
		save(file, false); // If save is called without a parameter it uses the class variable "file"
	}

	private void save(File saveToFile, boolean tempFile) throws UnsupportedEncodingException, FileNotFoundException {
		String tmp = createStringToBeSaved();
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(saveToFile), "UTF-8"));
		out.print(tmp);
		out.close();
		if (!tempFile) {
			handler.setChanged(false);
			RecentlyUsedFilesList.getInstance().add(saveToFile.getAbsolutePath());
		}
		Notifier.getInstance().showInfo(saveToFile.getAbsolutePath() + " saved");
	}

	private String chooseFileName(boolean ownXmlFormat, FileFilter filefilter, JFileChooser fileChooser) {
		String fileName = null;

		setAvailableFileFilters(ownXmlFormat, fileChooser);
		fileChooser.setFileFilter(filefilter);

		int returnVal = fileChooser.showSaveDialog(CurrentGui.getInstance().getGui().getMainFrame());
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File selectedFileWithExt = getFileWithExtension(fileChooser);
			if (selectedFileWithExt.exists()) {
				int overwriteQuestionResult = JOptionPane.showConfirmDialog(CurrentGui.getInstance().getGui().getMainFrame(), "File already exists! Overwrite?", "Overwrite File", JOptionPane.YES_NO_OPTION);
				if (overwriteQuestionResult == JOptionPane.NO_OPTION) {
					return chooseFileName(ownXmlFormat, filefilter, fileChooser);
				}
			}
			fileName = selectedFileWithExt.getAbsolutePath();
		}
		return fileName;
	}

	private String directory(String filePath) {
		File fileObject = new File(filePath);
		File fileObjectAbsolute = new File(fileObject.getAbsolutePath());
		return fileObjectAbsolute.getParent();
	}

	private String filename(String filePath) {
		File fileObject = new File(filePath);
		String filename = fileObject.getName();
		int extensionPos = filename.lastIndexOf(".");

		if (extensionPos > 0) {
			String filenameWithoutExtension = filename.substring(0, extensionPos);
			return filenameWithoutExtension;
		}
		else {
			return filename;
		}
	}

	/**
	 * If the filename of the filechooser has no extension, the extension from the filefilter is added to the name
	 * @param saveFileChooser2
	 */
	private File getFileWithExtension(JFileChooser fileChooser) {
		String extension = "." + fileextensions.get(fileChooser.getFileFilter());
		String filename = fileChooser.getSelectedFile().getAbsolutePath();
		if (!filename.endsWith(extension)) {
			filename += extension;
		}
		File selectedFileWithExt = new File(filename);
		return selectedFileWithExt;
	}

	/**
	 * Updates the available FileFilter to "only uxf/pxf" or "all but uxf/pxf"
	 *
	 * @param ownXmlFormat
	 *            If this param is set, only uxf/pxf is visible, otherwise all but uxf/pxf is visible
	 */
	private void setAvailableFileFilters(boolean ownXmlFormat, JFileChooser fileChooser) {
		if (ownXmlFormat) {
			fileChooser.resetChoosableFileFilters();
			fileChooser.addChoosableFileFilter(filterxml);
		}
		else {
			fileChooser.resetChoosableFileFilters();
			fileChooser.addChoosableFileFilter(filterbmp);
			fileChooser.addChoosableFileFilter(filtereps);
			fileChooser.addChoosableFileFilter(filtergif);
			fileChooser.addChoosableFileFilter(filterjpg);
			fileChooser.addChoosableFileFilter(filterpdf);
			fileChooser.addChoosableFileFilter(filterpng);
			fileChooser.addChoosableFileFilter(filtersvg);
		}
	}

	protected static class OwnFileFilter extends FileFilter {
		private final String format;
		private final String description;

		protected OwnFileFilter(String format, String description) {
			this.format = format;
			this.description = description;
		}

		@Override
		public boolean accept(File f) {
			return f.getName().endsWith("." + format) || f.isDirectory();
		}

		@Override
		public String getDescription() {
			return description + " (*." + format + ")";
		}

		public String getFormat() {
			return format;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (file == null ? 0 : file.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		DiagramFileHandler other = (DiagramFileHandler) obj;
		if (file == null) {
			if (other.file != null) {
				return false;
			}
		}
		else if (!file.equals(other.file)) {
			return false;
		}
		return true;
	}

}
