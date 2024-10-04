package gr.ntua.ivml.mint.api.handlers;

import java.util.HashMap;
import java.util.Map;

import gr.ntua.ivml.mint.api.RouterServlet.Handler;

public class Setup {

	public static Map<? extends String, ? extends Handler> handlers() {
		Map<String,Handler> result = new HashMap<>();
		result.put( "POST /translation/create",TranslationHandlers::startTranslation);
		result.put( "GET /translation/status", TranslationHandlers::statusTranslation);
		result.put( "POST /translation/apply", TranslationHandlers::applyTranslation);
		result.put( "GET /translation/review", TranslationHandlers::getReviewTranslation);
		result.put( "POST /translation/review", TranslationHandlers::postReviewTranslation);
		result.put( "GET /translation/list", TranslationHandlers::listTranslations);
		result.put( "GET /translation/downloadLiterals", TranslationHandlers::downloadTranslationLiterals);
		result.put( "POST /translation/pecatReview", TranslationHandlers::pecatReviewMerge);
		result.put( "DELETE /translation", TranslationHandlers::deleteTranslation);
		
		
		result.put( "GET /dataset/download", DatasetHandlers::downloadDatasetTarball);
		result.put( "GET /item/download", DatasetHandlers::downloadItemXml);
		
		// enrichments / annotations ... grr hate the naming mess
		result.put( "POST /annotation/upload", AnnotationHandlers::uploadAnnotation);
		result.put( "POST /annotation/uploadByUrl", AnnotationHandlers::uploadAnnationViaLink );
		result.put( "GET /annotation/download", AnnotationHandlers::downloadAnnotation);
		result.put( "GET /annotation/list", AnnotationHandlers::listAnnotation);
		result.put( "GET /annotation/listValues", AnnotationHandlers::listAnnotationValues);
		result.put( "DELETE /annotation", AnnotationHandlers::deleteAnnotation);
		result.put( "POST /annotation/update", AnnotationHandlers::editAnnotation);
		result.put( "POST /annotation/imageAnalysis", AnnotationHandlers::queueImageAnalysis);
		
		// test and apply same method ... both need the filter body 
		result.put( "POST /annotation/apply", AnnotationHandlers::applyAnnotation);
		// sandbox
		result.put( "GET /sandbox/create", EuropeanaSandbox::createSandboxEntry);
		
		//locks
		result.put( "GET /locks", LockHandlers::getLocks);
		result.put( "DELETE /locks", LockHandlers::deleteLock);
		
		return result;
	}

}
