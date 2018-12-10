/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
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

package org.openapitools.codegen.languages;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FilenameUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

// Base class for all Python code generator
public class PythonBaseCodegen extends DefaultCodegen implements CodegenConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonBaseCodegen.class);

    public static final String PACKAGE_URL = "packageUrl";

    protected String projectName; // for setup.py, e.g. petstore-api
    protected String packageName; // e.g. petstore_api
    protected String packageVersion;
    protected String packageUrl = "";    // for setup.py
    protected String testFolder = "test";    // Folder where to generate tests

    protected Boolean excludeTests = false; // Don't generate tests if true
    protected Boolean generateSourceCodeOnly = false;   // Generate only python code if true

    protected Map<Character, String> regexModifiers;

    public PythonBaseCodegen(
            String defaultTemplateDir,
            String defaultPackageName
            ) {
        super();

        languageSpecificPrimitives.clear();
        languageSpecificPrimitives.add("int");
        languageSpecificPrimitives.add("float");
        languageSpecificPrimitives.add("list");
        languageSpecificPrimitives.add("List");
        languageSpecificPrimitives.add("dict");
        languageSpecificPrimitives.add("Dict");
        languageSpecificPrimitives.add("bool");
        languageSpecificPrimitives.add("str");
        languageSpecificPrimitives.add("datetime");
        languageSpecificPrimitives.add("date");
        languageSpecificPrimitives.add("file");
        languageSpecificPrimitives.add("object");

        typeMapping.clear();
        typeMapping.put("integer", "int");
        typeMapping.put("float", "float");
        typeMapping.put("number", "float");
        typeMapping.put("long", "int");
        typeMapping.put("double", "float");
        typeMapping.put("array", "List");
        typeMapping.put("map", "Dict");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "str");
        typeMapping.put("date", "date");
        typeMapping.put("DateTime", "datetime");
        typeMapping.put("object", "object");
        typeMapping.put("file", "file");
        typeMapping.put("UUID", "str");
        // TODO binary should be mapped to byte array
        // mapped to String as a workaround
        typeMapping.put("binary", "str");
        typeMapping.put("ByteArray", "str");
        // map uuid to string for the time being
        typeMapping.put("UUID", "str");

        // from https://docs.python.org/3/reference/lexical_analysis.html#keywords
        setReservedWordsLowerCase(
                Arrays.asList(
                        // local variable name used in API methods (endpoints)
                        "all_params", "resource_path", "path_params", "query_params",
                        "header_params", "form_params", "local_var_files", "body_params", "auth_settings",
                        // @property
                        "property",
                        // python reserved words
                        "and", "del", "from", "not", "while", "as", "elif", "global", "or", "with",
                        "assert", "else", "if", "pass", "yield", "break", "except", "import",
                        "print", "class", "exec", "in", "raise", "continue", "finally", "is",
                        "return", "def", "for", "lambda", "try", "self", "None", "True", "False", "nonlocal"));

        modelPackage = "models";
        packageName = defaultPackageName;
        packageVersion = "1.0.0";
        /*
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        embeddedTemplateDir = templateDir = defaultTemplateDir;

        regexModifiers = new HashMap<Character, String>();
        regexModifiers.put('i', "IGNORECASE");
        regexModifiers.put('l', "LOCALE");
        regexModifiers.put('m', "MULTILINE");
        regexModifiers.put('s', "DOTALL");
        regexModifiers.put('u', "UNICODE");
        regexModifiers.put('x', "VERBOSE");

        cliOptions.clear();
        addOption(CodegenConstants.PROJECT_NAME, "python project name in setup.py (e.g. petstore-api).", null);
        addOption(CodegenConstants.PACKAGE_NAME, "python package name (convention: snake_case).", packageName);
        addOption(CodegenConstants.PACKAGE_VERSION, "python package version.", packageVersion);
        addOption(PACKAGE_URL, "python package URL to use in setup.py.", packageUrl);
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (StringUtils.isEmpty(System.getenv("PYTHON_POST_PROCESS_FILE"))) {
            LOGGER.info("Environment variable PYTHON_POST_PROCESS_FILE not defined so the Python code may not be properly formatted. To define it, try 'export PYTHON_POST_PROCESS_FILE=\"/usr/local/bin/yapf -i\"' (Linux/Mac)");
            LOGGER.info("NOTE: To enable file post-processing, 'enablePostProcessFile' must be set to `true` (--enable-post-process-file for CLI).");
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME));
        } else {
            additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName);
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            setPackageVersion((String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION));
        } else {
            additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion);
        }

        if (additionalProperties.containsKey(PACKAGE_URL)) {
            setPackageUrl((String) additionalProperties.get(PACKAGE_URL));
        } else {
            additionalProperties.put(PACKAGE_URL, packageUrl);
        }

        if (additionalProperties.containsKey(CodegenConstants.PROJECT_NAME)) {
            setProjectName((String) additionalProperties.get(CodegenConstants.PROJECT_NAME));
        } else {
            // default: set project based on package name
            // e.g. petstore_api (package name) => petstore-api (project name)
            setProjectName(packageName.replaceAll("_", "-"));
            additionalProperties.put(CodegenConstants.PROJECT_NAME, projectName);
        }


    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public void setPackageUrl(String packageUrl) {
        this.packageUrl = packageUrl;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    private static String dropDots(String str) {
        return str.replaceAll("\\.", "_");
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove ' to avoid code injection
        return input.replace("'", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // remove multiline comment
        return input.replace("'''", "'_'_'");
    }

    @Override
    public String sanitizeTag(String tag) {
        return sanitizeName(tag);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separatorChar + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separatorChar + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_");

        // e.g. PhoneNumberApi.py => phone_number_api.py
        return underscore(name) + "_api";
    }

    @Override
    public String toApiTestFilename(String name) {
        return "test_" + toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        // underscore the model file name
        // PhoneNumber => phone_number
        return underscore(dropDots(toModelName(name)));
    }

    @Override
    public String toModelTestFilename(String name) {
        return "test_" + toModelFilename(name);
    }

    @Override
    public String toModelImport(String name) {
        String modelImport;
        if (StringUtils.startsWithAny(name, "import", "from")) {
            modelImport = name;
        } else {
            modelImport = "from ";
            if (!"".equals(modelPackage())) {
                modelImport += modelPackage() + ".";
            }
            modelImport += toModelFilename(name) + " import " + name;
        }
        return modelImport;
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        // remove dollar sign
        name = name.replaceAll("$", "");

        // if it's all uppper case, convert to lower case
        if (name.matches("^[A-Z_]*$")) {
            name = name.toLowerCase(Locale.ROOT);
        }

        // underscore the variable name
        // petId => pet_id
        name = underscore(name);

        // remove leading underscore
        name = name.replaceAll("^_*", "");

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        return name;
    }

    @Override
    public String toParamName(String name) {
        // to avoid conflicts with 'callback' parameter for async call
        if ("callback".equals(name)) {
            return "param_callback";
        }

        // should be the same as variable name
        return toVarName(name);
    }


    @Override
    public String toModelName(String name) {
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.
        // remove dollar sign
        name = name.replaceAll("$", "");

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + camelize("model_" + name));
            name = "model_" + name; // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + camelize("model_" + name));
            name = "model_" + name; // e.g. 200Response => Model200Response (after camelize)
        }

        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix;
        }

        // camelize the model name
        // phone_number => PhoneNumber
        return camelize(name);
    }

    @Override
    public String toOperationId(String operationId) {
        // throw exception if method name is empty (should not occur as an auto-generated method name will be used)
        if (StringUtils.isEmpty(operationId)) {
            throw new RuntimeException("Empty method name (operationId) not allowed");
        }

        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(operationId)) {
            LOGGER.warn(operationId + " (reserved word) cannot be used as method name. Renamed to " + underscore(sanitizeName("call_" + operationId)));
            operationId = "call_" + operationId;
        }

        // operationId starts with a number
        if (operationId.matches("^\\d.*")) {
            LOGGER.warn(operationId + " (starting with a number) cannot be used as method name. Renamed to " + underscore(sanitizeName("call_" + operationId)));
            operationId = "call_" + operationId;
        }

        return underscore(sanitizeName(operationId));
    }

    /**
     * Return the default value of the property
     *
     * @param p OpenAPI property object
     * @return string presentation of the default value of the property
     */
    @Override
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isBooleanSchema(p)) {
            if (p.getDefault() != null) {
                if (p.getDefault().toString().equalsIgnoreCase("false"))
                    return "False";
                else
                    return "True";
            }
        } else if (ModelUtils.isDateSchema(p)) {
            // TODO
        } else if (ModelUtils.isDateTimeSchema(p)) {
            // TODO
        } else if (ModelUtils.isNumberSchema(p)) {
            if (p.getDefault() != null) {
                return p.getDefault().toString();
            }
        } else if (ModelUtils.isIntegerSchema(p)) {
            if (p.getDefault() != null) {
                return p.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(p)) {
            if (p.getDefault() != null) {
                if (Pattern.compile("\r\n|\r|\n").matcher((String) p.getDefault()).find())
                    return "'''" + p.getDefault() + "'''";
                else
                    return "'" + p.getDefault() + "'";
            }
        }

        return null;
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        // process enum in models
        return postProcessModelsEnum(objs);
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        postProcessPattern(parameter.pattern, parameter.vendorExtensions);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        if (StringUtils.isNotEmpty(property.pattern)) {
            addImport(model, "import re");
        }
        postProcessPattern(property.pattern, property.vendorExtensions);
    }

    /*
     * The OpenAPI pattern spec follows the Perl convention and style of modifiers. Python
     * does not support this in as natural a way so it needs to convert it. See
     * https://docs.python.org/2/howto/regex.html#compilation-flags for details.
     */
    public void postProcessPattern(String pattern, Map<String, Object> vendorExtensions) {
        if (pattern != null) {
            int i = pattern.lastIndexOf('/');

            //Must follow Perl /pattern/modifiers convention
            if (pattern.charAt(0) != '/' || i < 2) {
                throw new IllegalArgumentException("Pattern must follow the Perl "
                        + "/pattern/modifiers convention. " + pattern + " is not valid.");
            }

            String regex = pattern.substring(1, i).replace("'", "\\'");
            List<String> modifiers = new ArrayList<String>();

            for (char c : pattern.substring(i).toCharArray()) {
                if (regexModifiers.containsKey(c)) {
                    String modifier = regexModifiers.get(c);
                    modifiers.add(modifier);
                }
            }

            vendorExtensions.put("x-regex", regex);
            vendorExtensions.put("x-modifiers", modifiers);
        }
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reserved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        if (this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name; // add an underscore to the name
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();
            return getSchemaType(p) + "[" + getTypeDeclaration(inner) + "]";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema inner = ModelUtils.getAdditionalProperties(p);
            return getSchemaType(p) + "[str, " + getTypeDeclaration(inner) + "]";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);
        String type = null;
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping.get(openAPIType);
            if (languageSpecificPrimitives.contains(type)) {
                return type;
            }
        } else {
            type = toModelName(openAPIType);
        }
        return type;
    }


    @Override
    public void setParameterExampleValue(CodegenParameter p) {
        String example;

        if (p.defaultValue == null) {
            example = p.example;
        } else {
            p.example = p.defaultValue;
            return;
        }

        String type = p.baseType;
        if (type == null) {
            type = p.dataType;
        }

        if ("String".equalsIgnoreCase(type) || "str".equalsIgnoreCase(type)) {
            if (example == null) {
                example = p.paramName + "_example";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("Integer".equals(type) || "int".equals(type)) {
            if (p.minimum != null) {
                example = "" + (Integer.valueOf(p.minimum) + 1);
            }
            if (p.maximum != null) {
                example = "" + p.maximum;
            } else if (example == null) {
                example = "56";
            }
        } else if ("Long".equalsIgnoreCase(type)) {
            if (p.minimum != null) {
                example = "" + (Long.valueOf(p.minimum) + 1);
            }
            if (p.maximum != null) {
                example = "" + p.maximum;
            } else if (example == null) {
                example = "789";
            }
        } else if ("Float".equalsIgnoreCase(type) || "Double".equalsIgnoreCase(type)) {
            if (p.minimum != null) {
                example = "" + p.minimum;
            } else if (p.maximum != null) {
                example = "" + p.maximum;
            } else if (example == null) {
                example = "3.4";
            }
        } else if ("BOOLEAN".equalsIgnoreCase(type) || "bool".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "True";
            }
        } else if ("file".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "(BytesIO(b'some file data'), 'file.txt')";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("Date".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "2013-10-20";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("DateTime".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "2013-10-20T19:20:30+01:00";
            }
            example = "'" + escapeText(example) + "'";
        } else if (!languageSpecificPrimitives.contains(type)) {
            // type is a model class, e.g. User
            example = this.packageName + "." + type + "()";
        } else {
            LOGGER.warn("Type " + type + " not handled properly in setParameterExampleValue");
        }

        if (p.items != null && p.items.defaultValue != null) {
            example = p.items.defaultValue;
        }
        if (example == null) {
            if (Boolean.TRUE.equals(p.isListContainer)) {
                example = "[]";
            }
            else {
                example = "None";
            }
        } else if (Boolean.TRUE.equals(p.isListContainer)) {
            example = "[" + example + "]";
        } else if (Boolean.TRUE.equals(p.isMapContainer)) {
            example = "{'key': " + example + "}";
        }

        p.example = example;
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        String pythonPostProcessFile = System.getenv("PYTHON_POST_PROCESS_FILE");
        if (StringUtils.isEmpty(pythonPostProcessFile)) {
            return; // skip if PYTHON_POST_PROCESS_FILE env variable is not defined
        }

        // only process files with py extension
        if ("py".equals(FilenameUtils.getExtension(file.toString()))) {
            String command = pythonPostProcessFile + " " + file.toString();
            try {
                Process p = Runtime.getRuntime().exec(command);
                int exitValue = p.waitFor();
                if (exitValue != 0) {
                    LOGGER.error("Error running the command ({}). Exit value: {}", command, exitValue);
                } else {
                    LOGGER.info("Successfully executed: " + command);
                }
            } catch (Exception e) {
                LOGGER.error("Error running the command ({}). Exception: {}", command, e.getMessage());
            }
        }
    }

}
