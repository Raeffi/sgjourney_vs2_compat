package raeffi.sgjourney_vs2_compat;

import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class TransporterConfig
{
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("sgjourney_vs2_compat.properties");

    public static void load()
    {
        Properties props = new Properties();

        if (!Files.exists(CONFIG_PATH))
        {
            // Write defaults on first run
            props.setProperty("max_transporter_range", String.valueOf(TransportHelper.MAX_TRANSPORTER_RANGE));
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH))
            {
                props.store(w, "SGJourney VS2 Compat Config");
            }
            catch (IOException e) { e.printStackTrace(); }
            return;
        }

        try (Reader r = Files.newBufferedReader(CONFIG_PATH))
        {
            props.load(r);
            TransportHelper.MAX_TRANSPORTER_RANGE = Double.parseDouble(
                    props.getProperty("max_transporter_range",
                            String.valueOf(TransportHelper.MAX_TRANSPORTER_RANGE)));
        }
        catch (IOException | NumberFormatException e) { e.printStackTrace(); }
    }
}