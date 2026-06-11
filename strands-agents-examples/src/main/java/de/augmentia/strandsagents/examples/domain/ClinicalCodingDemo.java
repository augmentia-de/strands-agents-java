package de.augmentia.strandsagents.examples.domain;


import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.config.ModelFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Medical Document Processing Assistant Demo (Java).
 * 
 * This sample demonstrates a specialized healthcare agent that:
 * 1. Processes medical documents (simulated extraction).
 * 2. Identifies key medical information (diagnoses, medications, treatments).
 * 3. Enriches information with standardized medical codes (ICD-10, RxNorm, SNOMED CT).
 * 
 * Architecture:
 * MedicalAssistant (Specialist Agent)
 *   ├── Document Processor Tool (Simulated OCR/Extraction)
 *   ├── Medical Coding Tools (Terminology Lookup)
 *   └── Structured Linker Tools (Extraction & Coding)
 */
public class ClinicalCodingDemo {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("🏥 Welcome to the Java Medical Document Processing Assistant");
        
        ClinicalCodingDemo demo = new ClinicalCodingDemo();
        demo.runMedicalAgent();
    }

    public void runMedicalAgent() {
        // 1. Setup the Medical Specialist Agent
        Agent medicalAgent = new Agent(ModelFactory.createOpenAiFromEnv());
        medicalAgent.setSystemPrompt("You are a Senior Medical Information Analyst specializing in clinical document processing and standardized medical terminology.\n\n" +
                "Your core responsibilities:\n" +
                "1. **Content Extraction:** Process clinical documents (PDFs, clinical notes, images) to accurately extract the patient's medical narrative.\n" +
                "2. **Clinical Identification:** Identify all critical medical entities, including diagnoses, prescribed medications, and recommended treatments.\n" +
                "3. **Standardized Coding:** Enrich all identified entities with the correct medical codes:\n" +
                "   - **ICD-10 (CM/PCS):** For all diagnoses and clinical conditions.\n" +
                "   - **RxNorm:** For all pharmaceutical interventions and medications.\n" +
                "   - **SNOMED CT:** For all clinical procedures, treatments, and referrals.\n\n" +
                "Deliver high-precision, structured clinical summaries suitable for electronic health records (EHR).");

        // 2. Register Medical Tools
        medicalAgent.getToolRegistry().register(new MedicalCodingTools());
        medicalAgent.getToolRegistry().register(new DocumentProcessorTools());

        // 3. Example Clinical Scenario
        String clinicalNote = "Carlie had a seizure 2 weeks ago. She is complaining of frequent headaches. " +
                             "Nausea is also present. Meds: Topamax 50 mgs daily. " +
                             "Send referral order to neurologist.";
        
        System.out.println("\n[Clinical Input]: " + clinicalNote);
        System.out.println("\n🏥 Assistant is analyzing clinical data and linking codes...");
        
        var result = medicalAgent.execute("Extract and code the following clinical note: " + clinicalNote);

        System.out.println("\n==========================================");
        System.out.println("🩺 PROCESSED MEDICAL REPORT");
        System.out.println("==========================================");
        System.out.println(result.finalAnswer());
        System.out.println("==========================================");
    }

    // --- Specialized Medical Tools ---

    public static class MedicalCodingTools {
        
        @Tool("Gets ICD-10 codes for a given diagnosis")
        public String getIcd10Code(@P("The diagnosis description") String diagnosis) {
            ObjectNode node = mapper.createObjectNode();
            node.put("term", diagnosis);
            
            if (diagnosis.toLowerCase().contains("seizure")) {
                node.put("code", "G40.909");
                node.put("description", "Epilepsy, unspecified, not intractable, without status epilepticus");
            } else if (diagnosis.toLowerCase().contains("headache")) {
                node.put("code", "R51.9");
                node.put("description", "Headache, unspecified");
            } else {
                node.put("code", "Unspecified");
                node.put("description", "No direct match found in terminology server");
            }
            return node.toString();
        }

        @Tool("Gets RxNorm codes for a given medication")
        public String getRxNormCode(@P("The medication name") String medication) {
            ObjectNode node = mapper.createObjectNode();
            node.put("medication", medication);
            
            if (medication.toLowerCase().contains("topamax")) {
                node.put("code", "36926");
                node.put("description", "Topiramate 50 MG Oral Tablet");
            } else {
                node.put("code", "Unknown");
            }
            return node.toString();
        }

        @Tool("Gets SNOMED CT codes for a treatment or procedure")
        public String getSnomedCode(@P("The procedure or treatment") String procedure) {
            ObjectNode node = mapper.createObjectNode();
            node.put("procedure", procedure);
            
            if (procedure.toLowerCase().contains("neurologist")) {
                node.put("code", "306206005");
                node.put("description", "Referral to neurology service");
            } else {
                node.put("code", "Not Found");
            }
            return node.toString();
        }
    }

    public static class DocumentProcessorTools {
        @Tool("Processes a medical document file and returns extracted text")
        public String processDocument(@P("Path to the medical file (PDF/Image)") String filePath) {
            // Simulated OCR/Extraction
            return "SIMULATED CONTENT: Patient presents with chest pain and shortness of breath. " +
                   "History of hypertension. Current medication: Lisinopril 10mg.";
        }
    }
}
