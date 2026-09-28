package cn.mcxyd.xiyuanmarry.repository;
import java.nio.file.Path;
public final class SqliteMarriageRepository extends JdbcMarriageRepository {public SqliteMarriageRepository(Path path){super(DatabaseSettings.sqlite(path));}}

