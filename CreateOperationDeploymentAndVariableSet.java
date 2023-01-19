/*
 * DISCLAIMER OF WARRANTIES.  The following [enclosed] code is
 * sample code created by IBM Corporation.  This sample code is
 * not part of any standard or IBM product and is provided to you
 * solely for the purpose of assisting you in the development of
 * your applications.  The code is provided "AS IS", without
 * warranty of any kind.  IBM shall not be liable for any damages
 * arising out of your use of the sample code, even if they have
 * been advised of the possibility of such damages.
 */

package com.ibm.rules.decisioncenter.contribs;

import ilog.rules.teamserver.brm.*;
import ilog.rules.teamserver.client.IlrRemoteSessionFactory;
import ilog.rules.teamserver.dsm.*;
import ilog.rules.teamserver.model.*;
import org.eclipse.emf.ecore.EClass;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class CreateOperationDeploymentAndVariableSet {

	private static Logger logger = Logger.getLogger(CreateOperationDeploymentAndVariableSet.class.getName());

	private static final String DEPLOYABLE_RULES_QUERY = "Deployable Rules Query";
	private static final String EXTRACTOR_NAME = "All Deployable Rules Extractor";
	private static final String MY_OPERATION = "MyOperation";
	private static final String MY_DEPLOYMENT = "My Deployment";
	private static final String MY_VARSET = "MyVarSet";
	private static final String AUTO_QUOTE = "AutoQuote";
	private static final String DATA_VALIDATION = "DataValidation";
	private static final String EXTRACTION_QUERY_TEXT = "Find all business rules such that the status of each business rule is deployable";

	public static void main(String[] args) throws IlrConnectException, IlrApplicationException {
		IlrSession session = createSession();
		// Cleanup the create objects first (for testing only)
		cleanup(session);
		// Create a variable set
		IlrVariableSet variableSet = createVariableSet(session);
		logger.info("Created variable set: " + variableSet.getName());
		// Create an operation
		IlrOperation operation = createOperation(session);
		logger.info("Created operation: " + operation.getName());
		// Create a deployment config
		IlrDeployment deployment = createDeployment(session, operation);
		logger.info("Created deployment: " + deployment.getName());
	}

	private static IlrSession createSession() throws IlrConnectException {
		IlrRemoteSessionFactory sessionFactory = new IlrRemoteSessionFactory();
		sessionFactory.connect("rtsAdmin", "rtsAdmin", "http://localhost:8081/teamserver", "jdbc/ilogDataSource");
		return sessionFactory.getSession();
	}

	private static IlrVariableSet createVariableSet(IlrSession session) throws IlrApplicationException {
		IlrRuleProject decisionService = IlrSessionHelper.getProjectNamed(session, AUTO_QUOTE);
		IlrBranch decisionServiceMain = (IlrBranch) decisionService.getCurrentBaseline();
		IlrBrmPackage brm = session.getModelInfo().getBrmPackage();
		IlrVariableSet variableSet = IlrSessionHelper.createVariableSet(session);
		variableSet.setName(MY_VARSET);
		IlrCommitableObject cobject = new IlrCommitableObject(variableSet);
		cobject.setRootDetails(variableSet);
		IlrVariable var1 = IlrSessionHelper.createVariable(session);
		var1.setName("var1");
		var1.setBomType("java.lang.String");
		cobject.addModifiedElement(brm.getVariableSet_Variables(), var1);
		return (IlrVariableSet) session.getElementDetails(session.commit(decisionServiceMain, cobject));
	}

	private static IlrOperation createOperation(IlrSession session) throws IlrApplicationException {
		IlrRuleProject decisionService = IlrSessionHelper.getProjectNamed(session, AUTO_QUOTE);
		IlrRuleProject dataValidationProject = IlrSessionHelper.getProjectNamed(session, DATA_VALIDATION);
		IlrBranch decisionServiceMain = (IlrBranch) decisionService.getCurrentBaseline();
		IlrBranch dataValidationMain = (IlrBranch) dataValidationProject.getCurrentBaseline();
		IlrDsmPackage dsm = session.getDsmPackage();
		IlrBrmPackage brm = session.getModelInfo().getBrmPackage();
		// Create an extraction query
		IlrElementHandle queryHandle = createExtractionQuery(session, dataValidationMain);
		// Create an extractor
		createExtractor(session, dataValidationMain, queryHandle);
		// Create an operation
		IlrOperation operation = (IlrOperation) session.getElementDetails(session.createElement(dsm.getOperation()));
		operation.setName(MY_OPERATION);
		operation.setRawValue(dsm.getOperation_BusinessDisplayName(), "My Operation");
		operation.setRawValue(dsm.getOperation_RulesetName(), "MyOpRuleset");
		operation.setRawValue(dsm.getOperation_TargetRuleProject(), dataValidationProject);
		// Associate the extractor to the operation
		operation.setRawValue(dsm.getOperation_Extractor(), EXTRACTOR_NAME);
		operation.setRawValue(dsm.getOperation_UsingExtractor(), true);
		// Find the ruleflow contained in the project
		IlrDefaultSearchCriteria searchRuleflow = new IlrDefaultSearchCriteria(brm.getRuleflow());
		searchRuleflow.setBaseline(dataValidationMain);
		IlrRuleflow ruleflow = (IlrRuleflow) session.findElements(searchRuleflow).get(0);
		// Associate the ruleflow to the operation
		operation.setRawValue(dsm.getOperation_Ruleflow(), ruleflow);
		operation.setRawValue(dsm.getOperation_UsingRuleflow(), true);
		IlrCommitableObject cOperation = new IlrCommitableObject(operation);
		cOperation.setRootDetails(operation);
		// Create an input parameter mapped to the autoQuoteReq of the BOM project
		{
			IlrVariableSet bomVariableSet = findByName(session, decisionServiceMain, brm.getVariableSet(), "Parameters");
			IlrVariable autoQuoteReq = bomVariableSet.getVariable("autoQuoteReq");
			IlrOperationVariable opAutoQuoteReq = (IlrOperationVariable) session.getElementDetails(session.createElement(dsm.getOperationVariable()));
			opAutoQuoteReq.setRawValue(dsm.getOperationVariable_VariableName(), autoQuoteReq.getName());
			opAutoQuoteReq.setRawValue(dsm.getOperationVariable_VariableSet(), bomVariableSet);
			opAutoQuoteReq.setRawValue(dsm.getOperationVariable_Direction(), IlrDirectionKind.IN_LITERAL.toString());
			// Associate the new variable to the operation
			cOperation.addModifiedElement(dsm.getOperation_ReferencedVariables(), opAutoQuoteReq);
		}
		// Create an output parameter mapped to the validationResp variable of the DataValidation project
		{
			IlrVariableSet dataValidationVariableSet = findByName(session, decisionServiceMain, brm.getVariableSet(), "DataValidationParameters");
			IlrVariable validationResp = dataValidationVariableSet.getVariable("validationResp");
			IlrOperationVariable opValidationResp = (IlrOperationVariable) session.getElementDetails(session.createElement(dsm.getOperationVariable()));
			opValidationResp.setRawValue(dsm.getOperationVariable_VariableName(), validationResp.getName());
			opValidationResp.setRawValue(dsm.getOperationVariable_VariableSet(), dataValidationVariableSet);
			opValidationResp.setRawValue(dsm.getOperationVariable_Direction(), IlrDirectionKind.OUT_LITERAL.toString());
			// Associate the new variable to the operation
			cOperation.addModifiedElement(dsm.getOperation_ReferencedVariables(), opValidationResp);
		}
		// Save the operation
		return (IlrOperation) session.getElementDetails(session.commit(dataValidationMain, cOperation));
	}

	private static IlrDeployment createDeployment(IlrSession session, IlrOperation operation) throws IlrApplicationException {
		IlrRuleProject decisionService = IlrSessionHelper.getProjectNamed(session, AUTO_QUOTE);
		IlrRuleProject dataValidationProject = IlrSessionHelper.getProjectNamed(session, DATA_VALIDATION);
		IlrBranch decisionServiceMain = (IlrBranch) decisionService.getCurrentBaseline();
		IlrBranch dataValidationMain = (IlrBranch) dataValidationProject.getCurrentBaseline();
		IlrDsmPackage dsm = session.getDsmPackage();
		IlrBrmPackage brm = session.getModelInfo().getBrmPackage();
		// Create a DepOperation object, that associates the operation with the deployment
		IlrDepOperation depOperation = (IlrDepOperation) session.getElementDetails(session.createElement(dsm.getDepOperation()));
		depOperation.setRawValue(dsm.getDepOperation_Active(), true);
		depOperation.setRawValue(dsm.getDepOperation_OperationName(), operation.getName());
		depOperation.setRawValue(dsm.getDepOperation_Operation(), operation);
		IlrDeployment deployment = (IlrDeployment) session.getElementDetails(session.createElement(dsm.getDeployment()));
		IlrCommitableObject cDeployment = new IlrCommitableObject(deployment);
		cDeployment.setRootDetails(deployment);
		cDeployment.addModifiedElement(dsm.getDeployment_Operations(), depOperation);
		deployment.setName(MY_DEPLOYMENT);
		deployment.setRawValue(dsm.getDeployment_RuleAppName(), "myRuleApp");
		deployment.setRawValue(dsm.getDeployment_RuleAppVersion(), "1.0");
		deployment.setRawValue(dsm.getDeployment_VersionPolicies(), "1.0");
		// Version policy
		IlrDepVersionPolicy versionPolicy = (IlrDepVersionPolicy) session.getElementDetails(session.createElement(dsm.getDepVersionPolicy()));
		versionPolicy.setRawValue(dsm.getDepVersionPolicy_Label(), "Increment minor ruleset version numbers");
		versionPolicy.setRawValue(dsm.getDepVersionPolicy_RuleApp(), "INCREMENT_MINOR");
		versionPolicy.setRawValue(dsm.getDepVersionPolicy_Ruleset(), "INCREMENT_MINOR");
		versionPolicy.setRawValue(dsm.getDepVersionPolicy_Default(), true);
		versionPolicy.setRawValue(dsm.getDepVersionPolicy_Recurrent(), true);
		cDeployment.addModifiedElement(dsm.getDeployment_VersionPolicies(), versionPolicy);
		// Set ruleset.version property (mandatory)
		IlrDepOperationProperty operationProperty = (IlrDepOperationProperty) session.getElementDetails(session.createElement(dsm.getDepOperationProperty()));
		operationProperty.setRawValue(dsm.getDepOperationProperty_OperationReference(), operation);
		operationProperty.setName("ruleset.version");
		operationProperty.setValue("1.0");
		cDeployment.addModifiedElement(dsm.getDeployment_OperationProperties(), operationProperty);
		// Associate to a server
		IlrDepTarget deploymentTarget = (IlrDepTarget) session.getElementDetails(session.createElement(dsm.getDepTarget()));
		deploymentTarget.setRawValue(dsm.getDepTarget_Active(), true);
		deploymentTarget.setRawValue(dsm.getDepTarget_Name(), "Local Execution Server");
		cDeployment.addModifiedElement(dsm.getDeployment_Targets(), deploymentTarget);
		// Commit the deployment configuration
		return (IlrDeployment) session.getElementDetails(session.commit(decisionServiceMain, cDeployment));
	}

	private static void createExtractor(IlrSession session, IlrBranch branch, IlrElementHandle queryHandle) throws IlrApplicationException {
		IlrBrmPackage brm = session.getBrmPackage();
		IlrElementHandle extractorHandle = session.createElement(brm.getExtractor());
		IlrExtractor extractor = (IlrExtractor) session.getElementDetailsForThisHandle(extractorHandle);
		extractor.setName(EXTRACTOR_NAME);
		// Associate the query to the extractor
		extractor.setRawValue(brm.getExtractor_Query(), queryHandle);
		IlrProjectInfo projectInfo = branch.getProjectInfo();
		IlrCommitableObject co = new IlrCommitableObject(projectInfo);
		co.addModifiedElement(brm.getProjectInfo_Extractors(), extractor);
		// Save the extractor in the DB
		session.commit(branch, co);
	}

	private static IlrElementHandle createExtractionQuery(IlrSession session, IlrBranch branch) throws IlrApplicationException {
		IlrBrmPackage brm = session.getBrmPackage();
		// Create an extraction query
		IlrElementHandle queryHandle = session.createElement(brm.getQuery());
		IlrQuery query = (IlrQuery) session.getElementDetails(queryHandle);
		query.setName(DEPLOYABLE_RULES_QUERY);
		query.setRawValue(brm.getAbstractQuery_Definition(), EXTRACTION_QUERY_TEXT);
		// Save the query in the DB
		IlrCommitableObject cQuery = new IlrCommitableObject(query);
		cQuery.setRootDetails(query);
		queryHandle = session.commit(branch, cQuery);
		return queryHandle;
	}

	private static void cleanup(IlrSession session) throws IlrApplicationException {
		IlrRuleProject decisionService = IlrSessionHelper.getProjectNamed(session, AUTO_QUOTE);
		IlrBranch decisionServiceMain = (IlrBranch) decisionService.getCurrentBaseline();
		IlrRuleProject dataValidationProject = IlrSessionHelper.getProjectNamed(session, DATA_VALIDATION);
		IlrBranch dataValidationMain = (IlrBranch) dataValidationProject.getCurrentBaseline();
		IlrBrmPackage brm = session.getBrmPackage();
		IlrProjectInfo projectInfo = dataValidationMain.getProjectInfo();
		List<IlrExtractor> extractors = projectInfo.getExtractors();
		for (IlrExtractor extractor : extractors) {
			if (EXTRACTOR_NAME.equals(extractor.getName())) {
				IlrCommitableObject cProjectInfo = new IlrCommitableObject(projectInfo);
				cProjectInfo.addDeletedElement(brm.getProjectInfo_Extractors(), extractor);
				session.commit(dataValidationMain, cProjectInfo);
			}
		}
		deleteByName(session, decisionServiceMain, session.getBrmPackage().getQuery(), DEPLOYABLE_RULES_QUERY);
		deleteByName(session, decisionServiceMain, session.getDsmPackage().getOperation(), MY_OPERATION);
		deleteByName(session, decisionServiceMain, session.getDsmPackage().getDeployment(), MY_DEPLOYMENT);
		deleteByName(session, decisionServiceMain, session.getBrmPackage().getVariableSet(), MY_VARSET);
	}

	private static void deleteByName(IlrSession session, IlrBranch branch, EClass eClass, String name) throws IlrApplicationException {
		IlrModelElement element = findByName(session, branch, eClass, name);
		if (element != null) {
			session.setWorkingBaseline(branch);
			session.deleteElement(element);
		}
	}

	private static <T extends IlrModelElement> T findByName(IlrSession session, IlrBranch branch, EClass eClass, String name) throws IlrApplicationException {
		IlrDefaultSearchCriteria search = new IlrDefaultSearchCriteria(eClass,
																																	 Collections.singletonList(session.getBrmPackage().getModelElement_Name()),
																																	 Collections.singletonList(name));
		search.setBaseline(branch);
		List elements = session.findElements(search);
		if (elements.size() == 1) {
			return (T) elements.get(0);
		}
		return null;
	}

}
