/*
 * DSS - Digital Signature Services
 *
 * Copyright (C) 2013 European Commission, Directorate-General Internal Market and Services (DG MARKT), B-1049 Bruxelles/Brussel
 *
 * Developed by: 2013 ARHS Developments S.A. (rue Nicolas Bové 2B, L-1253 Luxembourg) http://www.arhs-developments.com
 *
 * This file is part of the "DSS - Digital Signature Services" project.
 *
 * "DSS - Digital Signature Services" is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * DSS is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * "DSS - Digital Signature Services".  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.europa.ec.markt.dss.validation102853.processes;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.validation102853.policy.ProcessParameters;
import eu.europa.ec.markt.dss.validation102853.processes.subprocesses.CryptographicVerification;
import eu.europa.ec.markt.dss.validation102853.processes.subprocesses.IdentificationOfTheSignersCertificate;
import eu.europa.ec.markt.dss.validation102853.processes.subprocesses.SignatureAcceptanceValidation;
import eu.europa.ec.markt.dss.validation102853.processes.subprocesses.ValidationContextInitialisation;
import eu.europa.ec.markt.dss.validation102853.processes.subprocesses.X509CertificateValidation;
import eu.europa.ec.markt.dss.validation102853.report.Conclusion;
import eu.europa.ec.markt.dss.validation102853.rules.AttributeName;
import eu.europa.ec.markt.dss.validation102853.rules.AttributeValue;
import eu.europa.ec.markt.dss.validation102853.rules.ExceptionMessage;
import eu.europa.ec.markt.dss.validation102853.rules.Indication;
import eu.europa.ec.markt.dss.validation102853.rules.NodeName;
import eu.europa.ec.markt.dss.validation102853.rules.NodeValue;
import eu.europa.ec.markt.dss.validation102853.xml.XmlDom;
import eu.europa.ec.markt.dss.validation102853.xml.XmlNode;

/**
 * This class creates the validation data (Basic Building Blocks) for all signatures.
 * <p/>
 * 5. Basic Building Blocks<br>
 * This clause presents basic building blocks that are useable in the signature validation process. Later clauses will
 * use these blocks to construct validation algorithms for specific scenarios.
 *
 * @author bielecro
 */
public class BasicBuildingBlocks implements NodeName, NodeValue, AttributeName, AttributeValue, Indication, ExceptionMessage {

	private static final Logger LOG = LoggerFactory.getLogger(BasicBuildingBlocks.class);

	private XmlDom diagnosticData;

	private void prepareParameters(final ProcessParameters params) {

		this.diagnosticData = params.getDiagnosticData();
		isInitialised();
	}

	private void isInitialised() {

		if (diagnosticData == null) {
			final String message = String.format(EXCEPTION_TCOPPNTBI, getClass().getSimpleName(), "diagnosticData");
			throw new DSSException(message);
		}
	}

	/**
	 * This method lunches the construction process of basic building blocks.
	 *
	 * @param params validation process parameters
	 * @return {@code XmlDom} representing the detailed report of this process.
	 */
	public XmlDom run(final XmlNode mainNode, final ProcessParameters params) {

		prepareParameters(params);
		LOG.debug(this.getClass().getSimpleName() + ": start.");

		params.setContextName(SIGNING_CERTIFICATE);

		final XmlNode basicBuildingBlocksNode = mainNode.addChild(BASIC_BUILDING_BLOCKS);

		final List<XmlDom> signatures = diagnosticData.getElements("/DiagnosticData/Signature");

		for (final XmlDom signature : signatures) {

			final String type = signature.getValue("./@Type");
			if (COUNTERSIGNATURE.equals(type)) {

				params.setCurrentValidationPolicy(params.getCountersignatureValidationPolicy());
			} else {

				params.setCurrentValidationPolicy(params.getValidationPolicy());
			}

			final Conclusion conclusion = new Conclusion();

			params.setSignatureContext(signature);
			/**
			 * In this case signatureContext and contextElement are equal, but this is not the case for
			 * TimestampsBasicBuildingBlocks
			 */
			params.setContextElement(signature);

			/**
			 * 5. Basic Building Blocks
			 */

			final String signatureId = signature.getValue("./@Id");
			final XmlNode signatureNode = basicBuildingBlocksNode.addChild(SIGNATURE);
			signatureNode.setAttribute(ID, signatureId);
			/**
			 * 5.1. Identification of the signer's certificate (ISC)
			 */
			final IdentificationOfTheSignersCertificate isc = new IdentificationOfTheSignersCertificate();
			final Conclusion iscConclusion = isc.run(params, MAIN_SIGNATURE);
			signatureNode.addChild(iscConclusion.getValidationData());
			if (!iscConclusion.isValid()) {

				signatureNode.addChild(iscConclusion.toXmlNode());
				continue;
			}
			conclusion.addInfo(iscConclusion);
			conclusion.addWarnings(iscConclusion);

			/**
			 * 5.2. Validation Context Initialisation (VCI)
			 */
			final ValidationContextInitialisation vci = new ValidationContextInitialisation();
			final Conclusion vciConclusion = vci.run(params, signatureNode);
			if (!vciConclusion.isValid()) {

				signatureNode.addChild(vciConclusion.toXmlNode());
				continue;
			}
			conclusion.addInfo(vciConclusion);
			conclusion.addWarnings(vciConclusion);

			/**
			 * 5.4 Cryptographic Verification (CV)
			 * --> We check the CV before XCV to not repeat the same check with LTV if XCV is not conclusive.
			 */
			final CryptographicVerification cv = new CryptographicVerification();
			final Conclusion cvConclusion = cv.run(params, signatureNode);
			if (!cvConclusion.isValid()) {

				signatureNode.addChild(cvConclusion.toXmlNode());
				continue;
			}
			conclusion.addInfo(cvConclusion);
			conclusion.addWarnings(cvConclusion);

			/**
			 * 5.5 Signature Acceptance Validation (SAV)
			 * --> We check the SAV before XCV to not repeat the same check with LTV if XCV is not conclusive.
			 */
			final SignatureAcceptanceValidation sav = new SignatureAcceptanceValidation();
			final Conclusion savConclusion = sav.run(params, signatureNode);
			if (!savConclusion.isValid()) {

				signatureNode.addChild(savConclusion.toXmlNode());
				continue;
			}
			conclusion.addInfo(savConclusion);
			conclusion.addWarnings(savConclusion);

			/**
			 * 5.3 X.509 Certificate Validation (XCV)
			 */
			final X509CertificateValidation xcv = new X509CertificateValidation();
			final Conclusion xcvConclusion = xcv.run(params, MAIN_SIGNATURE);
			signatureNode.addChild(xcvConclusion.getValidationData());
			if (!xcvConclusion.isValid()) {

				signatureNode.addChild(xcvConclusion.toXmlNode());
				continue;
			}
			conclusion.addInfo(xcvConclusion);
			conclusion.addWarnings(xcvConclusion);

			conclusion.setIndication(VALID);
			final XmlNode conclusionXmlNode = conclusion.toXmlNode();
			signatureNode.addChild(conclusionXmlNode);
		}
		final XmlDom bbbDom = basicBuildingBlocksNode.toXmlDom();
		params.setBBBData(bbbDom);
		return bbbDom;
	}
}
