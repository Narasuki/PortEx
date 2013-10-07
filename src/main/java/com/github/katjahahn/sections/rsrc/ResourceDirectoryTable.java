package com.github.katjahahn.sections.rsrc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.katjahahn.PEModule;
import com.github.katjahahn.StandardEntry;

public class ResourceDirectoryTable extends PEModule {

	private final static int ENTRY_SIZE = 8;
	private final static int RESOURCE_DIR_OFFSET = 16;
	private Map<ResourceDirectoryTableKey, StandardEntry> data;
	private final Map<String, String[]> rsrcDirSpec;
	private final byte[] tableBytes;

	private final List<ResourceDirectoryEntry> entries = new ArrayList<>();
	private final List<ResourceDirectoryTable> children = new ArrayList<>();

	private int nameEntries;
	private int idEntries;
	private Date stamp;
	private final int id;
	private final int offset;

	public ResourceDirectoryTable(Map<String, String[]> rsrcDirSpec,
			byte[] tableBytes, int id, int offset) throws IOException {
		this.rsrcDirSpec = rsrcDirSpec;
		this.tableBytes = tableBytes;
		this.id = id;
		this.offset = offset;
		load();
	}

	private void load() throws IOException {
		data = new HashMap<>();
		for (Entry<String, String[]> entry : rsrcDirSpec.entrySet()) {

			String[] specs = entry.getValue();
			int value = getBytesIntValue(tableBytes,
					Integer.parseInt(specs[1]), Integer.parseInt(specs[2]));
			String key = entry.getKey();

			if (key.equals("TIME_DATE_STAMP")) {
				this.stamp = getTimeDate(value);
			}

			if (key.equals("NR_OF_NAME_ENTRIES")) {
				nameEntries = value;
			} else if (key.equals("NR_OF_ID_ENTRIES")) {
				idEntries = value;
			}

			data.put(ResourceDirectoryTableKey.valueOf(key), new StandardEntry(
					key, specs[0], value));
		}
		if (nameEntries != 0 || idEntries != 0) {
			loadResourceDirEntries();
			loadChildren();
		}
	}

	private void loadChildren() throws IOException {
		int childId = id;
		for (ResourceDirectoryEntry entry : entries) {
			Integer address = entry.getSubDirRVA();

			if (address != null) {
				childId++;
				try {
					byte[] resourceBytes = Arrays.copyOfRange(tableBytes,
							address - offset, tableBytes.length);
					children.add(new ResourceDirectoryTable(rsrcDirSpec,
							resourceBytes, childId, address));
				} catch (IllegalArgumentException e) {
					System.err.println(e.getMessage() + entry.getInfo());
				}
			} 

		}
	}

	private void loadResourceDirEntries() throws IOException {
		for (int i = 0; i < nameEntries + idEntries; i++) {
			int offset = RESOURCE_DIR_OFFSET + i * ENTRY_SIZE;
			int endpoint = offset + ENTRY_SIZE;
			int entryNr = i + 1;
			byte[] entryBytes = Arrays
					.copyOfRange(tableBytes, offset, endpoint);
			if (i < nameEntries) {
				entries.add(new ResourceDirectoryEntry(true, entryBytes,
						entryNr, id));
			} else {
				entries.add(new ResourceDirectoryEntry(false, entryBytes,
						entryNr, id));
			}
		}
	}

	private Date getTimeDate(int seconds) {
		long millis = (long) seconds * 1000;
		return new Date(millis);
	}

	@Override
	public String getInfo() {
		StringBuilder b = new StringBuilder();
		b.append("** table header " + id + " **" + NL + NL);

		for (StandardEntry entry : data.values()) {

			int value = entry.value;
			String key = entry.key;
			String description = entry.description;

			if (key.equals("TIME_DATE_STAMP")) {
				b.append(description + ": ");
				b.append(getTimeDate(value) + NL);
			} else {
				b.append(description + ": " + value + NL);
			}
		}
		for (ResourceDirectoryEntry entry : entries) {
			b.append(entry.getInfo());
		}
		for(ResourceDirectoryTable child : children) {
			b.append(child.getInfo());
		}
		return b.toString();
	}
}