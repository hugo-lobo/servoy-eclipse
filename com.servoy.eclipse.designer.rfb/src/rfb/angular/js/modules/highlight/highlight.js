angular.module('highlight', ['editor']).run(function($pluginRegistry, $editorService, $selectionUtils,$timeout) {

	$pluginRegistry.registerPlugin(function(editorScope) {
		var utils = $selectionUtils.getUtilsForScope(editorScope);

		var highlightDiv = angular.element(document.querySelector('#highlight'))[0];
		var event = null;
		var enabled = true;
		var execute = null;

		function getHighlightNode(event) {
			if (utils.getDraggingFromPallete() != null && editorScope.getEditorContentRootScope().drop_highlight) {
				var drop = editorScope.getEditorContentRootScope().drop_highlight.split(".");
				var canDrop = utils.getDropNode(utils.getDraggingFromPallete(), null, drop[drop.length-1], event);
				if (canDrop && canDrop.dropAllowed && canDrop.dropTarget)
				{
					return canDrop.dropTarget;
				}	
			}
			return utils.getNode(event);
		}

		function drawHighlightDiv() {
			var node = getHighlightNode(event);
			if (node && enabled && !editorScope.highlight) {
				if (node.parentElement != undefined && node.parentElement.parentElement !== editorScope.glasspane) {
					if (node.clientWidth == 0 && node.clientHeight == 0 && node.firstElementChild) node = node.firstElementChild;
					if (!node.getBoundingClientRect) node = node.parentNode;
					highlightDiv.style.display = 'block';
					var rect = node.getBoundingClientRect();
					var left = rect.left;
					var top = rect.top;
					top = top + parseInt(angular.element(editorScope.glasspane.parentElement).css("padding-top").replace("px",""));
					left = left + parseInt(angular.element(editorScope.glasspane.parentElement).css("padding-left").replace("px",""));
					highlightDiv.style.left = left + 'px';
					highlightDiv.style.top = top + 'px';
					highlightDiv.style.height = rect.height + 'px';
					highlightDiv.style.width = rect.width + 'px';
					//get to the first dom element that is a servoy component or layoutContainer
					while (node.parentElement && !node.getAttribute("svy-id")) node = node.parentElement;
					if (!angular.element(node).hasClass("inheritedElement")) {
							highlightDiv.style.outline = "";
					}
					else {
							highlightDiv.style.outline = "1px solid #FFBBBB";
					}

					if (!editorScope.isAbsoluteFormLayout()) {
						var nodeParents = $(node).parents('[svy-id]');
						var statusBarTxt = '';
						for(var n = nodeParents.length - 1; n > -2; n--) {
							var currentNode = n > -1 ? $(nodeParents[n]) : $(node);
							var type = currentNode.attr('svy-layoutname');
							if(!type) type = currentNode.attr('svy-formelement-type');
							if(!type) type = currentNode.get(0).nodeName;
							var name = currentNode.attr('svy-name');
							if(!name) name = currentNode.attr('name');
							
							statusBarTxt += '<strong>' + type + '</strong>';
							if(name) statusBarTxt += ' [ ' + name + ' ] ';
							if(n > -1) statusBarTxt += ' / ';
						}
						$editorService.setStatusBarText(statusBarTxt); 
					}
				}
				else {
					highlightDiv.style.display = 'none';
					highlightDiv.style.outline = "";
					if (!editorScope.isAbsoluteFormLayout()) $editorService.setStatusBarText("");
				}
			}
			else {
				highlightDiv.style.display = 'none';
				highlightDiv.style.outline = "";
				if (!editorScope.isAbsoluteFormLayout()) $editorService.setStatusBarText("");
			}
		}
		
		function disableHighlightDiv(){
			highlightDiv.style.display = 'none';
			if (!editorScope.isAbsoluteFormLayout()) $editorService.setStatusBarText("");
			enabled = false;
		}
		function enableHighlightDiv(){
			enabled = true;
		}

		function onmousemove(e) {
			if (execute)
				$timeout.cancel(execute);
			event = e;
			execute = $timeout(drawHighlightDiv,300);
		}
		
		editorScope.registerDOMEvent("mousemove","CONTENTFRAME_OVERLAY", onmousemove); // real selection in editor content iframe
		editorScope.registerDOMEvent("mousedown","CONTENTFRAME_OVERLAY", disableHighlightDiv); // real selection in editor content iframe
		editorScope.registerDOMEvent("mouseup","CONTENTFRAME_OVERLAY", enableHighlightDiv); // real selection in editor content iframe
	});
});