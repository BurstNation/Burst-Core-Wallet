/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS) {
    var requestConfirmations = [];

    NRS.updateRemoteNodes = function() {
        var data = {state: "CONNECTED", includePeerInfo: true};
        NRS.sendRequest("getPeers", data, function (response) {
            if (response.peers) {
                NRS.remoteNodesMgr.nodes = {};
                NRS.remoteNodesMgr.addRemoteNodes(response.peers);
            }
            if (NRS.isUpdateRemoteNodes()) {
                setTimeout(function () {
                    NRS.updateRemoteNodes();
                }, 30000);
            }
        });
    };

    NRS.initRemoteNodesMgr = function (isTestnet, resolve, reject) {
        NRS.remoteNodesMgr = new RemoteNodesManager(isTestnet);
        if (NRS.isMobileApp()) {
            if (NRS.mobileSettings.remote_node_address == "") {
                NRS.remoteNodesMgr.addBootstrapNodes(resolve, reject);
            } else {
                NRS.remoteNodesMgr.addBootstrapNode(resolve, reject);
            }
        } else if (NRS.isUpdateRemoteNodes()) {
            NRS.updateRemoteNodes();
        }
    };

    NRS.requestNeedsConfirmation = function (requestType) {
        var plusIndex = requestType.indexOf("+");
        if (plusIndex > 0) {
            requestType = requestType.substring(0, plusIndex);
        }
        return !NRS.isRequirePost(requestType) && NRS.isRequestForwardable(requestType)
    };

    var prunableAttachments = [
        "PrunablePlainMessage", "PrunableEncryptedMessage", "UnencryptedPrunableEncryptedMessage", "ShufflingProcessing", "TaggedDataUpload"
    ];

    function normalizePrunableAttachment(transaction) {
        var attachment = transaction.attachment;
        if (attachment) {
            // Check if prunable attachment
            var isPrunableAttachment = false;
            for (var key in attachment) {
                if (!attachment.hasOwnProperty(key) || !key.startsWith("version.")) {
                    continue;
                }
                key = key.substring("version.".length);
                for (var i=0; i<prunableAttachments.length; i++) {
                    if (key == prunableAttachments[i]) {
                        isPrunableAttachment = true;
                    }
                }
            }
            if (!isPrunableAttachment) {
                return;
            }
            for (key in attachment) {
                if (!attachment.hasOwnProperty(key)) {
                    continue;
                }
                if (key.length < 4 || !(key.substring(key.length - 4, key.length).toLowerCase() == "hash")) {
                    delete attachment[key];
                }
            }
        }
    }

    NRS.getComparableResponse = function(origResponse, requestType) {
        delete origResponse.requestProcessingTime;
        delete origResponse.confirmations;
        if (requestType == "getBlock") {
            delete origResponse.nextBlock;
        } else if (origResponse.transactions) {
            var transactions = origResponse.transactions;
            for (var i=0; i<transactions.length; i++) {
                var transaction = transactions[i];
                delete transaction.confirmations;
                normalizePrunableAttachment(transaction);
            }
        }
        return JSON.stringify(origResponse);
    };

    NRS.confirmResponse = function(requestType, data, expectedResponse, requestRemoteNode) {
        if (NRS.requestNeedsConfirmation(requestType)) {
            try {
                // First clone the response so that we do not change it
                var expectedResponseStr = JSON.stringify(expectedResponse);
                expectedResponse = JSON.parse(expectedResponseStr);

                // Now remove all variable parts
                expectedResponseStr = NRS.getComparableResponse(expectedResponse, requestType);
            } catch(e) {
                NRS.logConsole("Cannot parse JSON response for request " + requestType);
                return;
            }
            var ignoredAddresses = [];
            if (requestRemoteNode) {
                ignoredAddresses.push(requestRemoteNode.address);
            }
            var nodes = NRS.remoteNodesMgr.getRandomNodes(NRS.mobileSettings.validators_count, ignoredAddresses);
            var confirmationReport = {processing: [], confirmingNodes: [], rejectingNodes: [], requestType: requestType};
            requestConfirmations.unshift(confirmationReport);
            if (requestConfirmations.length > 20) {
                requestConfirmations.pop();
            }
            function onConfirmation(response) {
                var fromNode = this;
                var index = confirmationReport.processing.indexOf(fromNode.announcedAddress);
                confirmationReport.processing.splice(index, 1);

                if (!response.errorCode) {
                    // here it's Ok to modify the response since it is only being used for comparison
                    var node = data["_extra"].node;
                    var type = data["_extra"].requestType;
                    NRS.logConsole("Confirm request " + type + " with node " + node.announcedAddress);
                    var responseStr = NRS.getComparableResponse(response, type);
                    if (responseStr == expectedResponseStr) {
                        confirmationReport.confirmingNodes.push(node);
                    } else {
                        NRS.logConsole(node.announcedAddress + " response defers from " + requestRemoteNode.announcedAddress + " response for " + type);
                        NRS.logConsole("Expected Response: " + expectedResponseStr);
                        NRS.logConsole("Actual   Response: " + responseStr);
                        confirmationReport.rejectingNodes.push(node);
                        NRS.updateConfirmationsIndicator();
                    }

                    if (confirmationReport.processing.length == 0) {
                        NRS.logConsole("onConfirmation:Request " + type +
                            " confirmations " + confirmationReport.confirmingNodes.length +
                            " rejections " + confirmationReport.rejectingNodes.length);
                        if (confirmationReport.rejectingNodes.length > 0) {
                            $.growl($.t("warning_request_confirmation_rejection",
                                { type: type, confirmations: confirmationReport.confirmingNodes.length, rejections: confirmationReport.rejectingNodes.length }
                            ))
                        }
                        NRS.updateConfirmationsIndicator();
                    }
                } else {
                    // Confirmation request received error
                    NRS.logConsole("Confirm request error " + response.errorDescription);
                }
            }

            for (var i=0; i<nodes.length; i++) {
                var node = nodes[i];
                if (node.isBlacklisted()) {
                    continue;
                }
                confirmationReport.processing.push(node.announcedAddress);
                ignoredAddresses.push(node.address);
                if (typeof data == "string") {
                    data = { "querystring": data };
                }
                data["_extra"] = { node: node, requestType: requestType };
                NRS.sendRequest(requestType, data, onConfirmation, { noProxy: true, remoteNode: node, doNotEscape: true });
            }
        }
    };

    NRS.updateConfirmationsIndicator = function () {
        var color = (62 << 16) | (169 << 8) | 64;
        var rejections = 0;
        var confirmations = 0;
        var hasRejections = false;
        for (var i=0; i<requestConfirmations.length; i++) {
            confirmations++; //the main remote node counts as 1 vote
            confirmations += requestConfirmations[i].confirmingNodes.length;
            rejections += requestConfirmations[i].rejectingNodes.length;
        }
        if (confirmations > 0) {
            var rejectionsRatio = rejections * 2 / confirmations; // It can't get worse than 1:1 ratio
            if (rejectionsRatio > 1) {
                rejectionsRatio = 1;
            }
            if (rejectionsRatio > 0) {
                var gradientStart = 0xeccc31;
                var gradientEnd = 0xa94442;
                var red = (gradientStart >> 16) * (1 - rejectionsRatio) + (gradientEnd >> 16) * rejectionsRatio;
                var green = ((gradientStart >> 8) & 0xff) * (1 - rejectionsRatio) + ((gradientEnd >> 8) & 0xff) * rejectionsRatio;
                var blue = (gradientStart & 0xff) * (1 - rejectionsRatio) + (gradientEnd & 0xff) * rejectionsRatio;
                color = (red << 16) | (green << 8) | blue;
                hasRejections = true;
            }
        }
        var indicatorIcon = $("#confirmation_rate_indicator i");
        if (hasRejections) {
            indicatorIcon.removeClass('fa-bolt');
            indicatorIcon.addClass('fa-exclamation');
        } else {
            indicatorIcon.addClass('fa-bolt');
            indicatorIcon.removeClass('fa-exclamation');
        }

        $("#confirmation_rate_indicator").css({'background-color': "#" + color.toString(16)});

        NRS.updateConfirmationsTable();

    };

    NRS.printRemoteAddresses = function (nodesList) {
        var result = "";
        for (var i=0; i<nodesList.length; i++) {
            result += '<a target="_blank" href="' + nodesList[i].getUrl() + '">' + nodesList[i].announcedAddress + '</a> ';
        }
        return result;
    };

    NRS.updateConfirmationsTable = function () {
        var requestConfirmationsInfoTable = $("#request_confirmations_info_table");
        var rows = "";

        for (var i=0; i<requestConfirmations.length; i++) {
            rows += "<tr>" +
                        "<td>" + String(requestConfirmations[i].requestType).escapeHTML() + "</td>" +
                        "<td>" + NRS.printRemoteAddresses(requestConfirmations[i].confirmingNodes) + "</td>" +
                        "<td>" + NRS.printRemoteAddresses(requestConfirmations[i].rejectingNodes) + "</td>" +
                    "</tr>";
        }
        requestConfirmationsInfoTable.find("tbody").empty().append(rows);
    };
	return NRS;
}(NRS || {}, jQuery));