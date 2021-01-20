package ca.qc.cvm.dba.scoutlog.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.StatementResult;
import org.neo4j.driver.types.Node;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import ca.qc.cvm.dba.scoutlog.entity.LogEntry;

public class LogDAO {
	/**
	 * M�thode permettant d'ajouter une entr�e
	 * 
	 * Note : Ne changer pas la structure de la m�thode! Elle
	 * permet de faire fonctionner l'ajout d'une entr�e du journal.
	 * Il faut donc que la compl�ter.
	 * 
	 * @param l'objet avec toutes les donn�es de la nouvelle entr�e
	 * @return si la sauvegarde a fonctionn�e
	 */
	public static boolean addLog(LogEntry log) {
		boolean success = false;
		
		System.out.println(log.toString());
		
		try {
			  Session session = Neo4jConnection.getConnection();
			  Map<String, Object> params = new HashMap<String, Object>();
			
			  params.put("p1", log.getDate());
			  params.put("p2", log.getName());
			  params.put("p3", log.getStatus());
			  
			if(log.getStatus() == "Normal") {
				session.run("CREATE (a:LogEntry {date: {p1}, name: {p2}, status:{p3}})", params);
			}
			else if(log.getStatus() == "Anormal") {
				params.put("p4", log.getReasons());
				session.run("CREATE (a:LogEntry {date: {p1}, name: {p2}, status:{p3}, reasons: {p4}})", params);
			}
			else{
				
				String key = log.getPlanetName();
				
				if (log.getImage() != null) {
					Database connection = BerkeleyConnection.getConnection();

					byte[] data = log.getImage();
					 
					try {
					    DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
					    DatabaseEntry theData = new DatabaseEntry(data);
					    connection.put(null, theKey, theData); 
					} 
					catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				params.put("p4", log.getNearPlanets());
				params.put("p5", log.getPlanetName());
				params.put("p6", log.getGalaxyName());
				params.put("p7", log.isHabitable());
				params.put("p8", key);
				session.run("CREATE (a:LogEntry {date: {p1}, name: {p2}, status:{p3}, nearPlanets: {p4}, planetName: {p5}, galaxyName: {p6}, isHabitable: {p7}, imageKey: {p8}})",params);
				
				for(String planet : log.getNearPlanets()) {
					params.put("p9", planet);
					
					session.run("MATCH (a:LogEntry),(b:LogEntry) WHERE a.planetName = {p5} AND b.planetName = {p9} CREATE (a)-[:Near]->(b)-[:Near]->(a)",params);	
				}
				
			}
			success = true;
			
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		
		return success;
	}
	
	/**
	 * Permet de retourner la liste de plan�tes d�j� explor�es
	 * 
	 * Note : Ne changer pas la structure de la m�thode! Elle
	 * permet de faire fonctionner l'ajout d'une entr�e du journal.
	 * Il faut donc que la compl�ter.
	 * 
	 * @return le nom des plan�tes d�j� explor�es
	 */
	public static List<String> getPlanetList() {
		List<String> planets = new ArrayList<String>();
		
		// Exemple...
		 
		 try {
			 	Session session = Neo4jConnection.getConnection();
				
				StatementResult result = session.run("MATCH (a:LogEntry) WHERE a.planetName IS NOT NULL RETURN a.planetName");
	
				while(result.hasNext()) {
					Record record = result.next();
					planets.add(record.get("a.planetName").asString());
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		 	 
		planets.add("Terre");
		planets.add("Solaria");
		planets.add("Dune");
		
		return planets;
	}
	
	/**
	 * Retourne l'entr�e selon sa position dans le temps.
	 * La derni�re entr�e est 0,
	 * l'avant derni�re est 1,
	 * l'avant avant derni�re est 2, etc.
	 * 
	 * Toutes les informations li�es � l'entr�e doivent �tre affect�es � 
	 * l'objet retourn�. 
	 * 
	 * 
	 * @param position (d�marre � 0)
	 * @return
	 */
	public static LogEntry getLogEntryByPosition(int position) {
		
		LogEntry log = null;
		
		 try {
			 	Session session = Neo4jConnection.getConnection();
				
				StatementResult index = session.run("CALL db.indexes() YIELD description WHERE description contains ':LogEntry(date)' RETURN *");
				if(!index.hasNext()) {
					session.run("CREATE INDEX ON :LogEntry(date)");
				} 
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("p1", position);
				StatementResult result = session.run("MATCH (a:LogEntry) RETURN a ORDER BY a.date DESC ", params);
				
				
				if(result.hasNext()) {
					Record record = result.next();
					Node node = record.get("a").asNode();
					
					if(node.get("status").asString() == "Normal") {
                        log = new LogEntry(node.get("date").asString(), node.get("name").asString(), node.get("status").asString());
                    }
                    else if(node.get("status").asString() == "Anormal") {
                        log = new LogEntry(node.get("date").asString(), node.get("name").asString(), node.get("status").asString(), node.get("reasons").asString());
                    }
                    else {
                        
                        byte[] retData = null;
                        Database connection = BerkeleyConnection.getConnection();

                        String key = node.get("imageKey").asString();

                        try {
                            DatabaseEntry theKey = new DatabaseEntry(key.getBytes("UTF-8"));
                            DatabaseEntry theData = new DatabaseEntry();
                         
                            if (connection.get(null, theKey, theData, LockMode.DEFAULT) == OperationStatus.SUCCESS) { 
                                retData = theData.getData();
                                String foundData = new String(retData, "UTF-8");

                            } 
                        
                        } 
                        catch (Exception e) {
                        	e.printStackTrace();
                        }
                          
                        List<String> nearPlanets = new ArrayList<>();
                        
                        if (node.get("nearPlanets").asList()!=null) {
	                        for(Object planet : node.get("nearPlanets").asList()) {
	                        	nearPlanets.add(planet.toString());
	                        }
                        }
                        
                        log = new LogEntry(node.get("date").asString(), node.get("name").asString(), node.get("status").asString(), node.get("reasons").asString(), nearPlanets, node.get("planetName").asString(), node.get("galaxyName").asString(),retData,node.get("isHabitable").asBoolean()); //nearPlanets: {p4}, planetName: {p5}, galaxyName: {p6}, isHabitable: {p7}, imageKey
                    }
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		return log;
	}
	
	/**
	 * Permet de supprimer une entr�e, selon sa position 
	 *  
	 * @param position de l'entr�e, identique � getLogEntryByPosition
	 * @return
	 */
	public static boolean deleteLog(int position) {
		boolean success = false;
		
		return success;
	}
	
	/**
	 * Doit retourner le nombre d'entr�es dans le journal de bord
	 * 
	 * Note : Ne changer pas la structure de la m�thode! Elle
	 * permet de faire fonctionner l'affichage de la liste des entr�es 
	 * du journal. Il faut donc que la compl�ter.
	 * 
	 * @return nombre total
	 */
	public static int getNumberOfEntries() {
		
		int nbNodes =0;
		
		 try {
			 	Session session = Neo4jConnection.getConnection();
				
			 	StatementResult result = session.run("MATCH (n) RETURN COUNT(n) as nbNode");
				nbNodes = result.next().get("nbNode").asInt();
	
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		
		
		return nbNodes;
	}
	
	/**
	 * Retourne le nombre de plan�tes habitables
	 * 
	 * @return nombre total
	 */
	public static int getNumberOfHabitablePlanets() {
		return 0;
	}
	
	/**
	 * Retourne entre 0 et 100 la moyenne d'entr�es de type exploration sur le
	 * nombre total d'entr�es
	 * 
	 * @return moyenne, entre 0 et 100
	 */
	public static int getExplorationAverage() {
		return 0;
	}

	
	/**
	 * Retourne le nombre de photos sauvegard�es
	 * 
	 * @return nombre total
	 */
	public static int getPhotoCount() {
		return 0;
	}
	

	/**
	 * Retourne le nom des derni�res plan�tes explor�es
	 * 
	 * @param limit nombre � retourner
	 * @return
	 */
	public static List<String> getLastVisitedPlanets(int limit) {
		List<String> planetList = new ArrayList<String>();
				
		return planetList;
	}
	
	/**
	 * Permet de trouver la galaxie avec le plus grand nombre de plan�tes habitables
	 * 
	 * @return le nom de la galaxie
	 */
	public static String getBestGalaxy() {
		return "";
	}
	
	/**
	 * Permet de trouver une chemin pour se rendre d'une plan�te � une autre 
	 * 
	 * @param fromPlanet
	 * @param toPlanet
	 * @return Liste du nom des plan�tes � parcourir, incluant "fromPlanet" et "toPlanet", ou null si aucun chemin trouv�
	 */
	public static List<String> getTrajectory(String fromPlanet, String toPlanet) {
		
		return null;
	}

	/**
	 * La liste des galaxies ayant le plus de plan�tes explor�es (en ordre d�croissant) 
	 * 
	 * @param limit Nombre � retourner
	 * @return List de nom des galaxies + le nombre de plan�tes visit�es, par exemple : Androm�de (7 plan�tes visit�es), ...
	 */	
	public static List<String> getExploredGalaxies(int limit) {
		List<String> galaxyList = new ArrayList<String>();

		return galaxyList;
	}
	
	/**
	 * Suppression de toutes les donn�es
	 */
	public static boolean deleteAll() {
		boolean success = false;
		
		return success;
	}
}
