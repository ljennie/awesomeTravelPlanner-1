package db.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import db.DBConnection;
import entity.Change;
import entity.Place;
import entity.Place.PlaceBuilder;
import external.GooglePlaceAPI;
import routePlannerAlgo.PlannerAlgo;

public class MySQLConnection implements DBConnection {
	private Connection conn;

	public MySQLConnection() {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver").getConstructor().newInstance();
			conn = DriverManager.getConnection(MySQLDBUtil.URL);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Connection getConn() {
		return conn;
	}

	@Override
	public void close() {
		try {
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public List<List<Place>> getInitialRecommend(String userID, int nDay) {
		int nPlaceDaily = 5;
		List<Place> places = getRandomKPlaces(nDay * nPlaceDaily);

		List<List<Place>> res = new ArrayList<>();
		for (int i = 0; i < nDay; i++) {
			List<Place> t = new ArrayList<>();
			for (int j = 0; j < nPlaceDaily; j++) {
				t.add(places.get(i * nPlaceDaily + j));
			}
			res.add(t);
		}

		deleteRoute(userID);
		saveRoute(userID, res, 1);

		return res;
	}

	List<Place> getRandomKPlaces(int n) {
		List<Place> res = new ArrayList<>();
		String sql = "SELECT * FROM places ORDER BY RAND() LIMIT " + String.valueOf(n);
		try {
			PreparedStatement statement = conn.prepareStatement(sql);
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				String placeID = rs.getString("place_id");
				String name = rs.getString("name");
				double lat = rs.getDouble("lat");
				double lon = rs.getDouble("lon");
				String url = rs.getString("imageURL");
				Place p = new PlaceBuilder().setPlaceID(placeID).setName(name).setLat(lat).setLon(lon).setURL(url)
						.setType("poi").build();
				res.add(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	@Override
	public void saveRoute(String userID, List<List<Place>> places, int offset) {
		// offset = 1 for initial recommend save, since the point of interest
		// intradayIndex starts from 1
		for (int i = 0; i < places.size(); i++) {
			List<Place> dailyPlace = places.get(i);
			for (int j = 0; j < dailyPlace.size(); j++) {
				Place place = dailyPlace.get(j);
				String sql = "INSERT IGNORE INTO routes VALUES (?, ?, ?, ?, ?)";
				try {
					PreparedStatement ps = conn.prepareStatement(sql);
					ps.setString(1, userID);
					ps.setString(2, place.getPlaceID());
					ps.setString(3, String.valueOf(i));
					ps.setString(4, String.valueOf(j + offset));
					ps.setString(5, place.getType());
					ps.execute();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void deleteRoute(String userID) {

		String sql = "DELETE FROM routes WHERE user_id = ? ";
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, userID);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Place getPlace(String placeID, String type) {
		String sql = "SELECT * FROM places WHERE place_id = ? ";
		try {
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, placeID);
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				String name = rs.getString("name");
				double lat = rs.getDouble("lat");
				double lon = rs.getDouble("lon");
				String url = rs.getString("imageURL");
				Place p = new PlaceBuilder().setPlaceID(placeID).setName(name).setLat(lat).setLon(lon).setURL(url)
						.setType(type).build();
				return p;
			}
			throw new Exception("Item not found in TABLE place for placeID = " + placeID);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public void savePlace(Place p) {
		String sql = "INSERT IGNORE INTO places VALUES (?, ?, ?, ?, ?)";
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, p.getPlaceID());
			ps.setString(2, p.getName());
			ps.setString(3, String.valueOf(p.getLat()));
			ps.setString(4, String.valueOf(p.getLon()));
			ps.setString(5, p.getImageURL());
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void savePlace(List<Place> places) {
		for (int i = 0; i < places.size(); i++) {
			savePlace(places.get(i));
		}
	}

	@Override
	public void savePlace(String placeID) {
		savePlace(GooglePlaceAPI.searchUsingPlaceID(placeID));
	}

	@Override
	public void updateSchedule(String userID, List<Change> newSchedule) {
		for (Change change : newSchedule) {
			try {
				if (change.getDay() != -1) {
					// update
					String sql = "UPDATE routes SET day = ?, index_of_day = ? WHERE user_id = ? AND place_id = ?";
					PreparedStatement ps = conn.prepareStatement(sql);
					ps.setInt(1, change.getDay());
					ps.setInt(2, change.getIntradayIndex());
					ps.setString(3, userID);
					ps.setString(4, change.getPlaceID());
					ps.execute();
				} else {
					// delete
					String sql = "DELETE FROM routes WHERE user_id = ? AND place_id = ?";
					PreparedStatement ps = conn.prepareStatement(sql);
					ps.setString(1, userID);
					ps.setString(2, change.getPlaceID());
					ps.execute();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	@Override
	public boolean registerUser(String userId, String password, String firstname, String lastname) {
		if (conn == null) {
			System.err.println("DB connection failed");
			return false;
		}

		try {
			String sql = "INSERT IGNORE INTO users VALUES (?, ?, ?, ?)";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, userId);
			ps.setString(2, password);
			ps.setString(3, firstname);
			ps.setString(4, lastname);

			return ps.executeUpdate() == 1;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean verifyLogin(String userId, String password) {
		if (conn == null) {
			return false;
		}

		try {
			String sql = "SELECT * FROM users WHERE user_id = ? AND password = ? ";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userId);
			statement.setString(2, password);
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	public List<List<Place>> generatePath(String userID, List<Place> startPlaces) {
		List<List<Place>> res = new ArrayList<List<Place>>();
		for (int i = 0; i < startPlaces.size(); i++) {
			res.add(generateDailyPath(userID, i, startPlaces.get(i)));
		}

		deleteRoute(userID);
		saveRoute(userID, res, 0);
		return res;
	}

	@Override
	public List<Place> getDailyPlaces(String userID, int day) {
		class Info implements Comparable<Info> {
			int intradayIndex;
			String placeID;
			String type;

			public Info(int intradayIndex, String placeID, String type) {
				this.intradayIndex = intradayIndex;
				this.placeID = placeID;
				this.type = type;
			}

			@Override
			public int compareTo(Info b) {
				if (type.equals("start")) {
					return -1;
				} else if (b.type.equals("start")) {
					return 1;
				} else {
					return Integer.valueOf(intradayIndex).compareTo(Integer.valueOf(intradayIndex));
				}
			}
		}

		try {
			// 1. get placeID for this user on this day
			String sql = "SELECT * FROM routes WHERE user_id = ? AND day = ? ";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userID);
			statement.setInt(2, day);
			ResultSet rs = statement.executeQuery();
			List<Info> routeInfo = new ArrayList<>();
			while (rs.next()) {
				routeInfo.add(new Info(rs.getInt("index_of_day"), rs.getString("place_id"), rs.getString("type")));
			}
			Collections.sort(routeInfo);

			// 2. query place database and get place objects
			List<Place> path = new ArrayList<>();
			for (Info info : routeInfo) {
				path.add(getPlace(info.placeID, info.type));
			}
			return path;

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public List<List<Place>> getPlaces(String userID) {
		// return an empty list if userID not exist
		// return null if error happens
		try {
			// 1. get placeID for this user on this day
			String sql = "SELECT day FROM routes WHERE user_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, userID);
			ResultSet rs = statement.executeQuery();
			int maxDay = -1;
			while (rs.next()) {
				maxDay = Math.max(maxDay, rs.getInt("day"));
			}
			// 2. get places for each day and aggregate
			List<List<Place>> places = new ArrayList<>();
			for (int i = 0; i <= maxDay; i++) {
				places.add(getDailyPlaces(userID, i));
			}
			return places;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public List<Place> generateDailyPath(String userID, int day, Place startPlace) {
		Place p = null;
		List<Place> middlePlaces = getDailyPlaces(userID, day);

		for (Place place : middlePlaces) {
			if (place.getType().equals("start")) {
				p = place;
				break;
			}
		}
		middlePlaces.remove(p);
		// middlePlaces.add(0, startPlace); // default algo, deprecated
		return PlannerAlgo.getPath(startPlace, middlePlaces);
	}

	@Override
	public void saveDistance(String startPlaceID, String endPlaceID, double distance) {
		String sql = "INSERT IGNORE INTO distances VALUES (?, ?, ?)";
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, startPlaceID);
			ps.setString(2, endPlaceID);
			ps.setDouble(3, distance);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Double getDistance(String startPlaceID, String endPlaceID) {
		try {
			// 1. get placeID for this user on this day
			String sql = "SELECT distance FROM distances WHERE start_place_id = ? AND end_place_id = ?";
			PreparedStatement statement = conn.prepareStatement(sql);
			statement.setString(1, startPlaceID);
			statement.setString(2, endPlaceID);
			ResultSet rs = statement.executeQuery();

			while (rs.next()) {
				return rs.getDouble("distance");
			}
			throw new Exception("no distance information found for (startPlaceID,endPlaceID) = (" + startPlaceID + ","
					+ endPlaceID + ")");
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

}
