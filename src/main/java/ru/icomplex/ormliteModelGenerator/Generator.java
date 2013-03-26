package ru.icomplex.ormliteModelGenerator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * User: artem
 * Date: 26.03.13
 * Time: 10:37
 */

class ModelGroup {
    String modelGroup;
    String projectName;

    ModelGroup(String modelGroup, String projectName) {
        this.modelGroup = modelGroup;
        this.projectName = projectName;
    }

    ModelGroup(String projectName) {
        this.projectName = projectName;
    }

    String getModelGroup() {
        return modelGroup;
    }

    void setModelGroup(String modelGroup) {
        this.modelGroup = modelGroup;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getDataScheme() {
        return modelGroup + ":" + projectName;
    }
}

public class Generator {
    String dbfile;
    String outPath;
    String classPath;
    ModelGroup modelGroup;

    public Generator(String dbfile, String classPath, ModelGroup modelGroup, String outPath) {
        this.classPath = classPath;
        this.modelGroup = modelGroup;
        this.dbfile = dbfile;
        this.modelGroup = modelGroup;
        this.outPath = outPath;
    }

    public void generate() throws Exception {
        Class.forName("org.sqlite.JDBC");

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbfile);

        PreparedStatement statementTables = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = \"table\"");
        ResultSet tablesResult = statementTables.executeQuery();
        List<TableFields> tableFieldsList = new ArrayList<>();
        while (tablesResult.next()) {
            String tableName = tablesResult.getString(1);

            PreparedStatement statementTableInfo = connection.prepareStatement("PRAGMA table_info(" + tableName + ");");
            ResultSet tableInfoResult = statementTableInfo.executeQuery();

            TableFields tableFields = new TableFields(tableName);

            while (tableInfoResult.next()) {
                FieldModel model = new FieldModel(tableInfoResult);

                tableFields.addField(model);
            }

            String classPath = generateClassPath(outPath);
            if (!classPath.isEmpty()) {
                if (writeModel(classPath, tableName, tableFields.generate(this.classPath," ru.ifacesoft.anu.model.Model"))) {
                    System.out.println(tableName + " модель создана в папку " + classPath);
                } else {
                    System.out.println("Не удалось записать" + tableName + "в папку " + classPath);
                }
            } else {
                System.out.println(" classPath пуст, модель " + tableName + " не записана");
            }

            tableFieldsList.add(tableFields);

            tableInfoResult.close();
            statementTableInfo.close();
        }

        generateMainDataScheme();
        generateModelDataScheme(tableFieldsList);
    }

    private String generateClassPath(String outPath) {
        String classDirectoryPath = classPath.replaceAll("\\.", "/") + "/model/";

        String path = outPath + "src/main/java/" + classDirectoryPath;
        File classDirectory = new File(path);

        return classDirectory.mkdirs() || (classDirectory.exists() && classDirectory.isDirectory()) ? path : "";
    }

    private boolean writeModel(String path, String className, String text) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path + TableFields.upFirstLetter(className) + ".java"));
            out.write(text);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean generateMainDataScheme() throws TransformerException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("config");
        doc.appendChild(rootElement);

        // path elements
        Element path = doc.createElement("path");
        rootElement.appendChild(path);

        // shorten way
        // path.setAttribute("id", "1");

        // firstname elements
        Element modelGroupElement = doc.createElement(modelGroup.getModelGroup());
        modelGroupElement.appendChild(doc.createTextNode(classPath + ".dataScheme"));
        path.appendChild(modelGroupElement);

        Element dataSchemeElement = doc.createElement("dataScheme");
        dataSchemeElement.appendChild(doc.createTextNode(modelGroup.getDataScheme()));
        rootElement.appendChild(dataSchemeElement);

        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);

        String directoryPath = outPath + "resources.ru.ifacesoft.anu.dataScheme".replaceAll(".", "/") + "/";
        String filePath = directoryPath + "DataScheme.xml";

        File dirFile = new File(directoryPath);
        dirFile.mkdirs();

        StreamResult result = new StreamResult(new File(filePath));

        // Output to console for testing
        // StreamResult result = new StreamResult(System.out);

        transformer.transform(source, result);
        return true;
    }

    private boolean generateModelDataScheme(List<TableFields> tableFieldsList) throws TransformerException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElementDataScheme = doc.createElement("config");
        doc.appendChild(rootElementDataScheme);

        // path elements
        Element model = doc.createElement("model");

        for (TableFields tableFields : tableFieldsList) {
            model.appendChild(doc.createTextNode(modelGroup.getModelGroup() + ":" + tableFields.tableName));
            if (!writeModelScheme(tableFields)) {
                System.out.println("Не удалось записать" + tableFields);
                return false;
            }
        }
        rootElementDataScheme.appendChild(model);


        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);

        String directoryPath = outPath + "resources." + classPath.replaceAll(".", "/") + "/dataScheme/";
        String filePath = directoryPath + modelGroup.getProjectName() + "DataScheme.xml";

        File dirFile = new File(directoryPath);
        dirFile.mkdirs();

        StreamResult result = new StreamResult(new File(filePath));

        // Output to console for testing
        // StreamResult result = new StreamResult(System.out);

        transformer.transform(source, result);
        return true;
    }

    private boolean writeModelScheme(TableFields tableFields) throws ParserConfigurationException, TransformerException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("config");
        doc.appendChild(rootElement);

        // path elements
        Element dataSource = doc.createElement("dataSource");

        Element modelGroupElement = doc.createElement(classPath + ".dataScheme." + modelGroup.projectName + "Scheme");
        modelGroupElement.appendChild(doc.createTextNode("dataSource"));
        dataSource.appendChild(modelGroupElement);

        rootElement.appendChild(dataSource);


        Element dataScheme = doc.createElement("dataScheme");
        dataScheme.appendChild(doc.createTextNode(modelGroup.getDataScheme()));

        rootElement.appendChild(dataSource);

        Element model = doc.createElement("model");
        Element primary = doc.createElement("primary");
        primary.appendChild(doc.createTextNode(tableFields.getPrimary()));
        model.appendChild(primary);
        Element secondary = doc.createElement("secondary");
        secondary.appendChild(doc.createTextNode(tableFields.getSecondary()));
        model.appendChild(secondary);


        rootElement.appendChild(model);

        // shorten way
        // path.setAttribute("id", "1");

        // firstname elements

        Element dataSchemeElement = doc.createElement("dataSchemeElement");
        dataSchemeElement.appendChild(doc.createTextNode(modelGroup.getDataScheme()));
        rootElement.appendChild(dataSchemeElement);

        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);

        String directoryPath = outPath + "resources." + classPath.replaceAll(".", "/") + "/model/";
        String filePath = directoryPath + "TableFields" + ".xml";

        File dirFile = new File(directoryPath);
        dirFile.mkdirs();

        StreamResult result = new StreamResult(new File(filePath));

        // Output to console for testing
        // StreamResult result = new StreamResult(System.out);

        transformer.transform(source, result);
        return true;
    }


}
