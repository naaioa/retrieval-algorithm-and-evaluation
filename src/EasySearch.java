import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class EasySearch {

	public static void main(String[] args) throws ParseException, IOException {
		easySearch search = new easySearch();
		System.out.println("Running the Search query");
		search.getRank("bloomington indiana");
		System.out.println("Completed Search");
	}
	
	public List<Document> getRank(String queryString)throws ParseException, IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter("./output/easySearchOutput.txt"));
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get("./res/index")));
		IndexSearcher searcher = new IndexSearcher(reader);		

		// Get the preprocessed query terms
		Analyzer analyzer = new StandardAnalyzer();
		QueryParser parser = new QueryParser("TEXT", analyzer);
		Query query = parser.parse(QueryParser.escape(queryString));
		Set<Term> queryTerms = new LinkedHashSet<Term>();
		searcher.createNormalizedWeight(query, false).extractTerms(queryTerms);
		
		//Print total number of documents (N)
		double N = reader.maxDoc();
		writer.append("\nTotal number of documents: "+ N);
		writer.newLine();
				
		//Store term frequency in documents 
		HashMap<String, HashMap<Integer, Integer>> termCounts = new HashMap<String, HashMap<Integer, Integer>>();
		HashMap<String, Double> IDF = new HashMap<String, Double>();
		//For Document Length
		HashMap<Integer, Float> length = new HashMap<Integer, Float>();
		
		
		//Print the terms in query
		writer.append("\nTerms in the query:");
		for (Term t : queryTerms) {
			writer.append("\n"+t.text());
			writer.newLine();
			
			//Get document frequency
			double k=reader.docFreq(new Term("TEXT", t.text()));
			
			writer.append("Number of documents containing the term "+ t.text()+ " for field \"TEXT\": "+k);
			writer.newLine();
			
			HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
			termCounts.put(t.text(), count);
			
			//compute IDF for the terms in query and save in Hashmap
			IDF.put(t.text(), Math.log(1 + (N/k)));
		}
		
		
		
		ClassicSimilarity dSimi = new ClassicSimilarity();
		List<LeafReaderContext> leafContexts = reader.getContext().reader().leaves();
		for (int i = 0; i < leafContexts.size(); i++) { 
			LeafReaderContext leafContext = leafContexts.get(i);
			int startDocNo = leafContext.docBase;
			int numberOfDoc = leafContext.reader().maxDoc();
			
			//Save document length
			for (int docId = 0; docId < numberOfDoc; docId++) {
				//Get normalized length (1/sqrt(numOfTokens)) of the document
				float normalizedDocLength = dSimi.decodeNormValue(leafContext.reader().getNormValues("TEXT").get(docId));
				float docLength = 1 / (normalizedDocLength * normalizedDocLength);
				length.put((docId + startDocNo), docLength);
			}
			
			for (Term t : queryTerms) {
				//Get term frequency from its postings
				PostingsEnum de = MultiFields.getTermDocsEnum(leafContext.reader(),"TEXT", new BytesRef(t.text()));
				
				int doc;
				if (de != null) {
					while ((doc = de.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
						termCounts.get(t.text()).put((de.docID() + startDocNo), de.freq());
					}
				}
			}
		}
		
		writer.newLine();
		writer.append("\n***********************RESULTS*****************************\n");
		writer.newLine();
		
		ArrayList<Document> documentList = new ArrayList<Document>();
		writer.append("Relevance score for document:");
		writer.newLine();
		for(int doc=0;doc<N; doc++){
			double value = 0;
			String docID = searcher.doc(doc).get("DOCNO");
			writer.append(docID + " ");
			for (Term t : queryTerms) {
				if(termCounts.get(t.text()).get(doc)!=null){
					double termValue = 0;
					//get relevance score for each query term
					termValue = IDF.get(t.text()) * termCounts.get(t.text()).get(doc) / length.get(doc);
					writer.append(termValue + " ");
					//get relevance score for document
					value += termValue;
				}
			}
			
			Document documentScore = new Document();
			documentScore.setScore(value);
			documentScore.setDocID(docID);
			documentScore.setDocNo(doc);
			documentList.add(documentScore);
			
			writer.append(value + " ");
			writer.newLine();
		}
		reader.close();
		writer.close();
		return documentList;
	}
	}
