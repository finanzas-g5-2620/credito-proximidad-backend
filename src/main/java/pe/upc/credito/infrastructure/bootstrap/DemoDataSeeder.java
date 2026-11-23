package pe.upc.credito.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de demostracion para probar el frontend sin cargar informacion a mano.
 *
 * <p>Se insertan solo si la base de datos esta vacia (no hay tiendas). Con
 * {@code DEMO_DATA_ENABLED=false} se desactiva por completo. Las contrasenas
 * se almacenan con BCrypt; la clave de todos los usuarios demo es
 * {@code password}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final StoreRepository stores;
    private final UserRepository users;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(StoreRepository stores,
                          UserRepository users,
                          CustomerRepository customers,
                          ProductRepository products,
                          PasswordEncoder passwordEncoder) {
        this.stores = stores;
        this.users = users;
        this.customers = customers;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stores.count() > 0) {
            log.info("Datos de demostracion omitidos: la base de datos ya contiene informacion");
            return;
        }

        List<StoreEntity> savedStores = stores.saveAll(List.of(
                store("Bodega Don Pepe", "20512345678", "Av. Los Olivos 450, Lima"),
                store("Minimarket La Esperanza", "20698765432", "Av. Grau 128, Trujillo")));
        StoreEntity donPepe = savedStores.get(0);
        StoreEntity esperanza = savedStores.get(1);

        String hash = passwordEncoder.encode("password");
        List<UserEntity> savedUsers = users.saveAll(List.of(
                user(null, "superadmin", hash, "Carlos", "Mendoza", "admin@sys.pe", "SUPERADMIN"),
                user(donPepe.getStoreId(), "pepe.admin", hash, "Jose", "Perez", "pepe@bodega.pe", "STOREADMIN"),
                user(esperanza.getStoreId(), "luis.admin", hash, "Luis", "Torres", "luis@esperanza.pe", "STOREADMIN"),
                user(donPepe.getStoreId(), "maria.quispe", hash, "Maria", "Quispe", "maria@mail.pe", "CUSTOMER"),
                user(donPepe.getStoreId(), "jose.ramirez", hash, "Jose", "Ramirez", "jose@mail.pe", "CUSTOMER"),
                user(donPepe.getStoreId(), "ana.torres", hash, "Ana", "Torres", "ana@mail.pe", "CUSTOMER"),
                user(esperanza.getStoreId(), "carlos.ruiz", hash, "Carlos", "Ruiz", "carlos@mail.pe", "CUSTOMER")));

        UserEntity maria = savedUsers.get(3);
        UserEntity jose = savedUsers.get(4);
        UserEntity ana = savedUsers.get(5);
        UserEntity carlos = savedUsers.get(6);

        customers.saveAll(List.of(
                customer(donPepe.getStoreId(), maria, "Calle Las Flores 210", "987654321",
                        "1500.00", 6, 25, 5, "EFECTIVA", "0.1800000", "EFECTIVA", "0.1200000"),
                customer(donPepe.getStoreId(), jose, "Av. Peru 1180", "912345678",
                        "800.00", 3, 20, 30, "NOMINAL", "0.2400000", "EFECTIVA", "0.1500000"),
                customer(donPepe.getStoreId(), ana, "Jr. Puno 455", "998877665",
                        "2000.00", 12, 25, 5, "EFECTIVA", "0.1500000", "EFECTIVA", "0.1000000"),
                customer(esperanza.getStoreId(), carlos, "Av. Grau 128", "955443322",
                        "1200.00", 6, 25, 5, "EFECTIVA", "0.2000000", "EFECTIVA", "0.1200000")));

        products.saveAll(List.of(
                product(donPepe.getStoreId(), "Saco de Arroz Extra 50kg",
                        "Arroz superior anejo en saco de 50 kg", "Costeno",
                        "Distribuidora Norte SAC", "SACO", "160.00", "175.00", 25, 5),
                product(donPepe.getStoreId(), "Aceite Vegetal Primor 1L",
                        "Aceite vegetal comestible de un litro", "Primor",
                        "Alicorp", "BOTELLA", "8.50", "9.50", 48, 12),
                product(donPepe.getStoreId(), "Azucar Rubia Cartavio 5kg",
                        "Azucar rubia en bolsa de cinco kilos", "Cartavio",
                        "Cartavio S.A.A.", "PAQUETE", "22.00", "24.50", 30, 10),
                product(donPepe.getStoreId(), "Leche Gloria Tarro 400g",
                        "Leche evaporada entera en tarro", "Gloria",
                        "Gloria S.A.", "LATA", "4.50", "5.20", 60, 15),
                product(donPepe.getStoreId(), "Fideos Don Vittorio 500g",
                        "Fideos tallarin en paquete de medio kilo", "Don Vittorio",
                        "Molitalia", "PAQUETE", "3.20", "3.80", 40, 10),
                product(donPepe.getStoreId(), "Atun Florida 170g",
                        "Atun en aceite en lata de 170 gramos", "Florida",
                        "Conservas Florida", "LATA", "5.50", "6.20", 35, 8),
                product(donPepe.getStoreId(), "Detergente Bolivar 780g",
                        "Detergente en polvo para ropa", "Bolivar",
                        "Alicorp", "PAQUETE", "11.90", "13.50", 20, 5),
                product(donPepe.getStoreId(), "Gaseosa Inca Kola 1.5L",
                        "Bebida gaseosa de un litro y medio", "Inca Kola",
                        "Coca-Cola Peru", "BOTELLA", "6.50", "7.50", 24, 6),
                product(esperanza.getStoreId(), "Arroz Costeno 5kg",
                        "Arroz extra en bolsa de cinco kilos", "Costeno",
                        "Distribuidora Norte SAC", "PAQUETE", "21.00", "23.00", 18, 5),
                product(esperanza.getStoreId(), "Aceite Cocinero 900ml",
                        "Aceite vegetal de 900 mililitros", "Cocinero",
                        "Alicorp", "BOTELLA", "8.90", "9.90", 22, 6),
                product(esperanza.getStoreId(), "Leche Laive 1L",
                        "Leche fresca entera de un litro", "Laive",
                        "Laive S.A.", "BOTELLA", "4.80", "5.50", 15, 5)));

        log.info("Datos de demostracion insertados: 2 tiendas, 7 usuarios, 4 clientes y 11 productos");
    }

    private StoreEntity store(String businessName, String taxId, String address) {
        StoreEntity store = new StoreEntity();
        store.setBusinessName(businessName);
        store.setTaxId(taxId);
        store.setAddress(address);
        store.setStatus("ACTIVE");
        return store;
    }

    private UserEntity user(Integer storeId, String username, String passwordHash,
                            String firstName, String lastName, String email, String userType) {
        UserEntity user = new UserEntity();
        user.setStoreId(storeId);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setUserType(userType);
        user.setStatus("ACTIVE");
        return user;
    }

    private CustomerEntity customer(Integer storeId, UserEntity user, String homeAddress,
                                    String phoneNumber, String creditLimit, int maxCreditMonths,
                                    int cutoffDay, int paymentDueDay, String compensatoryType,
                                    String compensatoryValue, String penaltyType, String penaltyValue) {
        CustomerEntity customer = new CustomerEntity();
        customer.setStoreId(storeId);
        customer.setUserId(user.getUserId());
        customer.setHomeAddress(homeAddress);
        customer.setPhoneNumber(phoneNumber);
        customer.setCreditLimit(new BigDecimal(creditLimit));
        customer.setMaxCreditMonths(maxCreditMonths);
        customer.setCutoffDay(cutoffDay);
        customer.setPaymentDueDay(paymentDueDay);
        customer.setCompensatoryRateType(compensatoryType);
        customer.setCompensatoryRateValue(new BigDecimal(compensatoryValue));
        customer.setPenaltyRateType(penaltyType);
        customer.setPenaltyRateValue(new BigDecimal(penaltyValue));
        customer.setCreditCurrency("PEN");
        customer.setFavourBalance(BigDecimal.ZERO.setScale(2));
        return customer;
    }

    private ProductEntity product(Integer storeId, String name, String description, String brand,
                                  String supplier, String unitOfMeasure, String cashPrice,
                                  String creditPrice, int currentStock, int minStock) {
        ProductEntity product = new ProductEntity();
        product.setStoreId(storeId);
        product.setName(name);
        product.setDescription(description);
        product.setBrand(brand);
        product.setSupplier(supplier);
        product.setUnitOfMeasure(unitOfMeasure);
        product.setCashPrice(new BigDecimal(cashPrice));
        product.setCreditListPrice(new BigDecimal(creditPrice));
        product.setAllowsMonthlyPayments(true);
        product.setCurrentStock(currentStock);
        product.setMinStock(minStock);
        product.setStatus("AVAILABLE");
        return product;
    }
}
